package com.mailpulseai.ai.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mailpulseai.ai.service.AIEmailProcessorService;
import com.mailpulseai.ai.service.EmailProcessedEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * CONSUMES from:  'new-email'
 * PUBLISHES to:   'email-processed'
 *
 * Orchestrates the full AI pipeline per email:
 *   1. Classify (URGENT / IMPORTANT / CLIENT / NEWSLETTER / SPAM)
 *   2. Summarise (2-3 bullet points)
 *   3. Draft reply (skipped for SPAM/NEWSLETTER)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EmailProcessingConsumer {

    private static final String TOPIC_NEW_EMAIL       = "new-email";
    private static final String TOPIC_EMAIL_PROCESSED = "email-processed";

    private final AIEmailProcessorService aiService;
    private final EmailProcessedEventService persistService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    private static int emailCounter = 0;

    @KafkaListener(
        topics = TOPIC_NEW_EMAIL,
        groupId = "ai-processing-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleNewEmail(Map<String, Object> event) {
        String emailId = (String) event.get("emailId");
        String subject = (String) event.get("subject");
        String body    = (String) event.get("bodyText");

        log.info("AI Processing received email-id: {} | subject: '{}'", emailId, subject);

        if (emailId == null || emailId.isBlank()) {
            log.error("Received new-email event with null/blank emailId — skipping");
            return;
        }

        try {
            // Step 1: Classify via AI
            String classifyJson = aiService.classify(subject, body);
            classifyJson = stripMarkdownFences(classifyJson);

            if (classifyJson == null || classifyJson.isBlank() || classifyJson.equals("{}")) {
                log.warn("AI returned empty classification for email-id={}. Defaulting to IMPORTANT.", emailId);
                classifyJson = "{\"category\":\"IMPORTANT\",\"score\":50,\"reason\":\"Classification failed\"}";
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> classification;
            try {
                classification = objectMapper.readValue(classifyJson, Map.class);
            } catch (Exception jsonEx) {
                log.warn("Failed to parse AI JSON for email-id={}: '{}'. Defaulting.", emailId, classifyJson);
                classification = java.util.Map.of("category", "IMPORTANT", "score", 50, "reason", "Parse error");
            }

            String category = (String) classification.get("category");
            Object scoreObj = classification.get("score");
            int score = scoreObj instanceof Number ? ((Number) scoreObj).intValue() : 50;
            String reason   = String.valueOf(classification.getOrDefault("reason", ""));

            if (!isValidCategory(category)) {
                log.warn("AI returned unknown category '{}', defaulting to IMPORTANT", category);
                category = "IMPORTANT";
            }

            // Step 2: Summarise
            String summary = aiService.summarise(subject, body);

            // Step 3: Draft a reply (null for spam/newsletter)
            String draftReply = aiService.draftReply(subject, body, category);

            // Build the enriched event for downstream services
            Map<String, Object> processedEvent = new HashMap<>();
            processedEvent.put("emailId",         emailId);
            processedEvent.put("senderEmail",     (String) event.get("senderEmail"));
            processedEvent.put("senderName",      (String) event.get("senderName"));
            processedEvent.put("subject",         subject);
            processedEvent.put("category",        category);
            processedEvent.put("importanceScore", score);
            processedEvent.put("classifyReason",  reason);
            processedEvent.put("summary",         summary);
            processedEvent.put("draftReply",      draftReply);
            processedEvent.put("receivedAt",      (String) event.get("receivedAt"));

            // Persist to DB for dashboard
            persistService.saveProcessedEmail(processedEvent);

            // Publish so WhatsApp service and others can react
            kafkaTemplate.send(TOPIC_EMAIL_PROCESSED, emailId, processedEvent);
            log.info("Published email-processed for id={} category={} score={}", emailId, category, score);

        } catch (Exception e) {
            log.error("Failed to process email-id={}: {}", emailId, e.getMessage(), e);
        } finally {
            emailCounter++;
            try {
                if (emailCounter % 5 == 0) {
                    log.info("Processed 5 emails. Taking a 15-second cooldown to respect Groq limits...");
                    Thread.sleep(15000);
                } else {
                    // Small delay between regular emails to spread out requests
                    Thread.sleep(2000);
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Strips markdown code fences Claude sometimes adds around JSON.
     * E.g.: ```json\n{...}\n``` → {...}
     */
    private String stripMarkdownFences(String text) {
        if (text == null) return "{}";
        text = text.trim();
        if (text.startsWith("```")) {
            text = text.replaceFirst("```[a-zA-Z]*\\n?", "");
            if (text.endsWith("```")) {
                text = text.substring(0, text.lastIndexOf("```")).trim();
            }
        }
        return text;
    }

    private boolean isValidCategory(String category) {
        return category != null &&
               java.util.Set.of("URGENT", "IMPORTANT", "CLIENT", "NEWSLETTER", "SPAM")
                            .contains(category.toUpperCase());
    }
}
