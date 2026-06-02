package com.mailpulseai.monolith.ai;

import com.mailpulseai.monolith.entity.ProcessedEmailEntity;
import com.mailpulseai.monolith.entity.ProcessedEmailEntity.EmailCategory;
import com.mailpulseai.monolith.entity.ProcessedEmailEntity.ReplyStatus;
import com.mailpulseai.monolith.repository.ProcessedEmailRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Persists the AI processing results to the processed_emails table.
 * Called by EmailProcessingConsumer after Claude has classified, summarised,
 * and drafted a reply for an email.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailProcessedEventService {

    private final ProcessedEmailRepository repository;

    public ProcessedEmailRepository getRepository() { return repository; }

    public void saveProcessedEmail(Map<String, Object> event) {
        String emailId = (String) event.get("emailId");

        // Avoid reprocessing if somehow the event is delivered twice (Kafka at-least-once)
        if (repository.existsById(emailId)) {
            log.warn("Email {} already processed — skipping duplicate save", emailId);
            return;
        }

        ProcessedEmailEntity entity = new ProcessedEmailEntity();
        entity.setEmailId(emailId);
        entity.setSenderEmail((String) event.get("senderEmail"));
        entity.setSenderName((String) event.get("senderName"));
        entity.setSubject((String) event.get("subject"));
        entity.setSummary((String) event.get("summary"));
        entity.setDraftReply((String) event.get("draftReply"));
        entity.setClassifyReason((String) event.get("classifyReason"));
        entity.setProcessedAt(LocalDateTime.now());

        // Parse receivedAt from string if present
        try {
            String receivedAt = (String) event.get("receivedAt");
            if (receivedAt != null) {
                entity.setReceivedAt(LocalDateTime.parse(receivedAt));
            }
        } catch (Exception e) {
            log.debug("Could not parse receivedAt: {}", e.getMessage());
            entity.setReceivedAt(LocalDateTime.now());
        }

        // Parse category safely
        try {
            entity.setCategory(EmailCategory.valueOf((String) event.get("category")));
        } catch (Exception e) {
            log.warn("Unknown category '{}', defaulting to IMPORTANT", event.get("category"));
            entity.setCategory(EmailCategory.IMPORTANT);
        }

        // Parse score
        Object scoreObj = event.get("importanceScore");
        if (scoreObj instanceof Number n) {
            entity.setImportanceScore(n.intValue());
        }

        // Spam and newsletters don't need reply approval
        boolean needsReply = entity.getCategory() != EmailCategory.SPAM
            && entity.getCategory() != EmailCategory.NEWSLETTER;
        entity.setReplyStatus(needsReply ? ReplyStatus.PENDING_APPROVAL : ReplyStatus.IGNORED);

        repository.save(entity);
        log.info("Saved processed email {} | category={} | score={}",
            emailId, entity.getCategory(), entity.getImportanceScore());
    }
}
