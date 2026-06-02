package com.mailpulseai.monolith.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mailpulseai.monolith.entity.EmailEntity;
import com.mailpulseai.monolith.entity.ProcessedEmailEntity;
import com.mailpulseai.monolith.event.EmailProcessedEvent;
import com.mailpulseai.monolith.event.NewEmailEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmailProcessingListener {

    private final AIEmailProcessorService aiService;
    private final EmailProcessedEventService persistService;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    // AtomicInteger so concurrent async threads can safely update the counter
    private static final AtomicInteger emailCounter = new AtomicInteger(0);

    @Async
    @EventListener
    public void handleNewEmail(NewEmailEvent event) {
        EmailEntity email = event.getEmail();
        String emailId = email.getId();
        String subject = email.getSubject();
        String body    = email.getBodyText();

        log.info("AI Processing Event received emailId: {} | subject: '{}'", emailId, subject);

        try {
            // Step 1: Classify
            String classifyJson = aiService.classify(subject, body);
            classifyJson = stripMarkdownFences(classifyJson);

            if (classifyJson == null || classifyJson.isBlank() || classifyJson.equals("{}")) {
                classifyJson = "{\"category\":\"IMPORTANT\",\"score\":50,\"reason\":\"Classification failed\"}";
            }

            Map<String, Object> classification;
            try {
                classification = objectMapper.readValue(classifyJson, Map.class);
            } catch (Exception jsonEx) {
                classification = java.util.Map.of("category", "IMPORTANT", "score", 50, "reason", "Parse error");
            }

            String category = (String) classification.get("category");
            Object scoreObj = classification.get("score");
            int score = scoreObj instanceof Number ? ((Number) scoreObj).intValue() : 50;
            String reason   = String.valueOf(classification.getOrDefault("reason", ""));

            if (!isValidCategory(category)) {
                category = "IMPORTANT";
            }

            // Step 2: Summarise
            String summary = aiService.summarise(subject, body);

            // Step 3: Draft reply
            String draftReply = aiService.draftReply(subject, body, category);

            // Save to database
            Map<String, Object> processedEvent = new HashMap<>();
            processedEvent.put("emailId",         emailId);
            processedEvent.put("senderEmail",     email.getSenderEmail());
            processedEvent.put("senderName",      email.getSenderName());
            processedEvent.put("subject",         subject);
            processedEvent.put("category",        category);
            processedEvent.put("importanceScore", score);
            processedEvent.put("classifyReason",  reason);
            processedEvent.put("summary",         summary);
            processedEvent.put("draftReply",      draftReply);
            processedEvent.put("receivedAt",      email.getReceivedAt().toString());

            persistService.saveProcessedEmail(processedEvent);

            // Find the saved entity to publish
            ProcessedEmailEntity processedEntity = persistService.getRepository().findById(emailId).orElse(null);
            if (processedEntity != null) {
                // Publish Spring Event instead of Kafka
                eventPublisher.publishEvent(new EmailProcessedEvent(this, processedEntity));
                log.info("Published EmailProcessedEvent for id={} category={} score={}", emailId, category, score);
            }

        } catch (Exception e) {
            log.error("Failed to process emailId={}: {}", emailId, e.getMessage(), e);
        } finally {
            int count = emailCounter.incrementAndGet();
            try {
                if (count % 5 == 0) {
                    log.info("Processed 5 emails. Cooldown of 15 seconds to respect rate limits...");
                    Thread.sleep(15000);
                } else {
                    Thread.sleep(2000);
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
    }

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
