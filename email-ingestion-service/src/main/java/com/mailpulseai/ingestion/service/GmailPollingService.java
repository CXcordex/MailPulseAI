package com.mailpulseai.ingestion.service;

import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;
import com.mailpulseai.ingestion.entity.EmailEntity;
import com.mailpulseai.ingestion.kafka.EmailEventPublisher;
import com.mailpulseai.ingestion.repository.EmailRepository;
import com.mailpulseai.ingestion.util.EmailParserUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Polls the Gmail inbox every 60 seconds.
 * Fetches ALL inbox emails (read + unread) and uses database deduplication
 * to only process new ones. The user's Gmail is never modified.
 *
 * Flow:
 *   1. Fetch newest 50 inbox messages from Gmail API
 *   2. Skip any already in our DB (by Gmail message ID)
 *   3. Parse sender, subject, body for new ones
 *   4. Save to PostgreSQL via Hibernate
 *   5. Publish a 'new-email' Kafka event for AI processing
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GmailPollingService {

    private final Gmail gmailClient;
    private final EmailRepository emailRepository;
    private final EmailEventPublisher eventPublisher;
    private final EmailParserUtil parserUtil;

    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void onStartup() {
        log.info("Application ready. Spawning background thread to run initial inbox poll in 10 seconds...");
        new Thread(() -> {
            try {
                Thread.sleep(10000); // Wait 10 seconds for Kafka & Eureka to stabilise
                pollInbox();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    @org.springframework.scheduling.annotation.Schedules({
        @Scheduled(cron = "0 0 8,12,15,18,22 * * ?", zone = "Asia/Kolkata"),
        @Scheduled(cron = "0 59 23 * * ?", zone = "Asia/Kolkata")
    })
    public void pollInbox() {
        log.info("Running scheduled batch poll of Gmail inbox...");
        try {
            // Fetch newest 25 inbox emails from the past 24 hours
            var listResponse = gmailClient
                    .users().messages()
                    .list("me")
                    .setLabelIds(List.of("INBOX"))
                    .setQ("newer_than:1d")
                    .setMaxResults(25L)
                    .execute();

            if (listResponse == null) {
                log.debug("Gmail API returned null response.");
                return;
            }

            List<Message> messages = listResponse.getMessages();

            if (messages == null || messages.isEmpty()) {
                log.debug("No messages found in inbox.");
                return;
            }

            int newCount = 0;
            for (Message msg : messages) {
                try {
                    // DB deduplication — skip if we've already processed this email
                    if (emailRepository.existsByGmailMessageId(msg.getId())) {
                        continue;
                    }

                    Message full = gmailClient.users().messages()
                            .get("me", msg.getId())
                            .setFormat("full")
                            .execute();

                    EmailEntity entity = parserUtil.parse(full);
                    entity.setStatus(EmailEntity.ProcessingStatus.RECEIVED);
                    emailRepository.save(entity);

                    eventPublisher.publishNewEmail(entity);

                    entity.setStatus(EmailEntity.ProcessingStatus.PUBLISHED);
                    emailRepository.save(entity);

                    newCount++;
                    log.info("Published new-email event for message ID: {}", entity.getId());
                } catch (Exception e) {
                    log.error("Error processing message {}: {}", msg.getId(), e.getMessage(), e);
                }
            }
            log.info("Poll complete. {} new emails processed out of {} fetched.", newCount, messages.size());
        } catch (Exception e) {
            log.error("Error polling Gmail inbox: {}", e.getMessage(), e);
        }
    }
}
