package com.mailpulseai.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * Multi-provider AI email processor with automatic fallback.
 *
 * Provider rotation order (fastest / most generous first):
 *   1. Groq        — llama-3.1-8b-instant  (fastest, but 6k TPM limit)
 *   2. NVIDIA NIM  — meta/llama-3.1-8b-instruct (generous free tier)
 *   3. NVIDIA NIM  — second key (extra headroom)
 *   4. Google Gemini Flash — gemini-1.5-flash-latest (1M TPM free)
 *   5. OpenRouter  — llama-3.1-8b-instruct:free (last resort)
 *
 * On 429 Rate-Limit or 4xx error, it instantly tries the next provider.
 * 2xx from ANY provider is accepted and returned immediately.
 */
@Service
@Slf4j
public class AIEmailProcessorService {

    // ── Provider configuration ──────────────────────────────────────────────

    private record Provider(String name, String url, String apiKey,
                            String model, String authHeader) {}

    @org.springframework.beans.factory.annotation.Value("${spring.ai.openai.api-key:}")
    private String envGroqApiKey;

    private final List<Provider> providers = new java.util.ArrayList<>();

    private static final String GEMINI_URL =
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash-latest:generateContent";

    @jakarta.annotation.PostConstruct
    public void initProviders() {
        // 1. Add configured Groq API Key from environment first (if present)
        String groqKey = (envGroqApiKey != null && !envGroqApiKey.isBlank())
                ? envGroqApiKey
                : System.getenv("GROQ_API_KEY");

        if (groqKey != null && !groqKey.isBlank()) {
            log.info("Registering Groq provider with API key from environment configuration.");
            providers.add(new Provider(
                "Groq-Env",
                "https://api.groq.com/openai/v1/chat/completions",
                groqKey.trim(),
                "llama-3.1-8b-instant",
                "Bearer"
            ));
        }

        // 2. Add fallback providers if environment variables are set
        String fallbackGroq1 = System.getenv("FALLBACK_GROQ_KEY_1");
        if (fallbackGroq1 != null && !fallbackGroq1.isBlank()) {
            providers.add(new Provider(
                "Groq",
                "https://api.groq.com/openai/v1/chat/completions",
                fallbackGroq1.trim(),
                "llama-3.1-8b-instant",
                "Bearer"
            ));
        }

        String nvidiaKey1 = System.getenv("NVIDIA_API_KEY_1");
        if (nvidiaKey1 != null && !nvidiaKey1.isBlank()) {
            providers.add(new Provider(
                "NVIDIA-NIM-1",
                "https://integrate.api.nvidia.com/v1/chat/completions",
                nvidiaKey1.trim(),
                "meta/llama-3.1-8b-instruct",
                "Bearer"
            ));
        }

        String nvidiaKey2 = System.getenv("NVIDIA_API_KEY_2");
        if (nvidiaKey2 != null && !nvidiaKey2.isBlank()) {
            providers.add(new Provider(
                "NVIDIA-NIM-2",
                "https://integrate.api.nvidia.com/v1/chat/completions",
                nvidiaKey2.trim(),
                "meta/llama-3.1-8b-instruct",
                "Bearer"
            ));
        }

        String fallbackGroq2 = System.getenv("FALLBACK_GROQ_KEY_2");
        if (fallbackGroq2 != null && !fallbackGroq2.isBlank()) {
            providers.add(new Provider(
                "Groq-2ndKey",
                "https://api.groq.com/openai/v1/chat/completions",
                fallbackGroq2.trim(),
                "llama-3.1-8b-instant",
                "Bearer"
            ));
        }

        String openRouterKey = System.getenv("OPENROUTER_API_KEY");
        if (openRouterKey != null && !openRouterKey.isBlank()) {
            providers.add(new Provider(
                "OpenRouter",
                "https://openrouter.ai/api/v1/chat/completions",
                openRouterKey.trim(),
                "meta-llama/llama-3.1-8b-instruct:free",
                "Bearer"
            ));
        }
    }

    private String getGeminiApiKey() {
        String key = System.getenv("GEMINI_API_KEY");
        if (key != null && !key.isBlank()) {
            return key.trim();
        }
        String fallbackKey = System.getenv("FALLBACK_GEMINI_API_KEY");
        if (fallbackKey != null && !fallbackKey.isBlank()) {
            return fallbackKey.trim();
        }
        return "";
    }

    // ── Constructor / HTTP Client ───────────────────────────────────────────

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public AIEmailProcessorService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    // ── System Prompts ──────────────────────────────────────────────────────

    private static final String CLASSIFY_SYSTEM_PROMPT = """
        You are an expert email classifier for a personal AI assistant.
        Classify the email into EXACTLY ONE of these categories:
          SPAM, IMPORTANT, URGENT, NEWSLETTER, CLIENT
        
        Rules:
        - URGENT: needs action within 24 hours (deadlines, emergencies, time-sensitive)
        - IMPORTANT: needs attention but not time-critical
        - CLIENT: from a business client or partner
        - NEWSLETTER: subscription / bulk marketing / digest emails
        - SPAM: unsolicited, phishing, promotional junk
        
        Also return an importance score 0-100 (100 = highest priority).
        
        Respond ONLY with valid JSON, no markdown, no explanation:
        {"category": "IMPORTANT", "score": 87, "reason": "one short sentence"}
        """;

    private static final String SUMMARISE_SYSTEM_PROMPT = """
        You are an expert at summarising emails for busy professionals.
        Summarise the given email in 2-3 bullet points, max 20 words each.
        Focus on: what is asked, what action is needed, any deadline.
        Be direct and factual. No filler words.
        Return plain text with • bullets, no JSON, no markdown headers.
        """;

    private static final String DRAFT_SYSTEM_PROMPT = """
        You are a professional email assistant writing replies on behalf of Sayan,
        a final-year IT student and AI engineer intern applicant.
        
        Write a professional, concise, friendly reply to the given email.
        - Do NOT use "I hope this email finds you well" or other filler openers
        - Sign off as: Best regards,\\nSayan
        - Max 100 words
        - Match the tone of the original email
        - Return plain text only, no markdown
        """;

    // ── Core fallback caller ────────────────────────────────────────────────

    private String callWithFallback(String systemPrompt, String userMessage) {

        // First try OpenAI-compatible providers
        for (Provider p : providers) {
            String result = callOpenAiCompatible(p, systemPrompt, userMessage);
            if (result != null && !result.isBlank()) {
                return result;
            }
        }

        // Then try Google Gemini as an additional fallback
        log.info("All OpenAI-compatible providers failed. Trying Google Gemini Flash...");
        String geminiResult = callGemini(systemPrompt, userMessage);
        if (geminiResult != null && !geminiResult.isBlank()) {
            return geminiResult;
        }

        log.error("All AI providers exhausted. Returning empty response.");
        return "";
    }

    /** Call any OpenAI-compatible endpoint (Groq, NVIDIA NIM, OpenRouter). */
    private String callOpenAiCompatible(Provider p, String systemPrompt, String userMessage) {
        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("model", p.model());
            payload.put("temperature", 0.3);
            payload.put("max_tokens", 512);

            ArrayNode messages = payload.putArray("messages");
            messages.addObject().put("role", "system").put("content", systemPrompt);
            messages.addObject().put("role", "user").put("content", userMessage);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(p.url()))
                    .header("Content-Type", "application/json")
                    .header("Authorization", p.authHeader() + " " + p.apiKey())
                    .timeout(Duration.ofSeconds(20))
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root    = objectMapper.readTree(response.body());
                JsonNode choices = root.path("choices");
                if (choices.isArray() && choices.size() > 0) {
                    String text = choices.get(0).path("message").path("content").asText().trim();
                    log.info("Success via {}", p.name());
                    return text;
                }
            } else {
                // On 429 (rate limit) or any 4xx/5xx, log and fall through to next provider
                log.warn("[{}] HTTP {} — switching to next provider. Body: {}",
                        p.name(), response.statusCode(),
                        response.body().substring(0, Math.min(200, response.body().length())));
            }

        } catch (Exception e) {
            log.warn("[{}] Exception: {} — switching to next provider.", p.name(), e.getMessage());
        }
        return null;
    }

    /** Call Google Gemini REST API (different format from OpenAI). */
    private String callGemini(String systemPrompt, String userMessage) {
        try {
            // Gemini uses a "contents" array with "parts"
            ObjectNode payload = objectMapper.createObjectNode();
            ArrayNode contents = payload.putArray("contents");

            ObjectNode part = contents.addObject();
            part.put("role", "user");
            ArrayNode parts = part.putArray("parts");
            // Gemini doesn't support system roles in this format, so we prepend the prompt
            parts.addObject().put("text", systemPrompt + "\n\n" + userMessage);

            // Generation config
            ObjectNode genConfig = payload.putObject("generationConfig");
            genConfig.put("temperature", 0.3);
            genConfig.put("maxOutputTokens", 512);

            String url = GEMINI_URL + "?key=" + getGeminiApiKey();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(25))
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                String text = root
                    .path("candidates").get(0)
                    .path("content")
                    .path("parts").get(0)
                    .path("text").asText().trim();
                log.info("Success via Google Gemini Flash");
                return text;
            } else {
                log.warn("[Gemini] HTTP {} — {}", response.statusCode(),
                        response.body().substring(0, Math.min(200, response.body().length())));
            }

        } catch (Exception e) {
            log.warn("[Gemini] Exception: {}", e.getMessage());
        }
        return null;
    }

    // ── Public API ──────────────────────────────────────────────────────────

    public String classify(String subject, String body) {
        log.info("Classifying email: '{}'", subject);
        return callWithFallback(CLASSIFY_SYSTEM_PROMPT,
            "Subject: " + subject + "\n\nBody:\n" + truncate(body, 1500));
    }

    public String summarise(String subject, String body) {
        log.info("Summarising email: '{}'", subject);
        return callWithFallback(SUMMARISE_SYSTEM_PROMPT,
            "Subject: " + subject + "\n\nBody:\n" + truncate(body, 2000));
    }

    public String draftReply(String subject, String body, String category) {
        if (List.of("SPAM", "NEWSLETTER").contains(category)) {
            return null;
        }
        log.info("Drafting reply for email: '{}'", subject);
        return callWithFallback(DRAFT_SYSTEM_PROMPT,
            "Subject: " + subject + "\n\nOriginal email:\n" + truncate(body, 1500));
    }

    private String truncate(String text, int maxChars) {
        if (text == null) return "";
        return text.length() > maxChars ? text.substring(0, maxChars) + "..." : text;
    }
}
