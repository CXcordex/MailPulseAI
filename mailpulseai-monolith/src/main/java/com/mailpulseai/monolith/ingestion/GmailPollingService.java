package com.mailpulseai.monolith.ingestion;

import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;
import com.mailpulseai.monolith.entity.EmailEntity;
import com.mailpulseai.monolith.event.NewEmailEvent;
import com.mailpulseai.monolith.repository.EmailRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class GmailPollingService {

    private final Gmail gmailClient;
    private final EmailRepository emailRepository;
    private final EmailParserUtil parserUtil;
    private final ApplicationEventPublisher eventPublisher;

    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void onStartup() {
        log.info("Application ready. Spawning background thread to run initial inbox poll in 5 seconds...");
        new Thread(() -> {
            try {
                Thread.sleep(5000);
                pollInbox();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    @Scheduled(fixedDelay = 60000)
    public void pollInbox() {
        log.info("Running scheduled batch poll of Gmail inbox...");
        try {
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
                    // Check if we've already processed this email
                    if (emailRepository.existsById(msg.getId())) {
                        continue;
                    }

                    Message full = gmailClient.users().messages()
                            .get("me", msg.getId())
                            .setFormat("full")
                            .execute();

                    EmailEntity entity = parserUtil.parse(full);
                    entity.setStatus(EmailEntity.ProcessingStatus.RECEIVED);
                    emailRepository.save(entity);

                    // Publish Spring Application Event instead of Kafka
                    eventPublisher.publishEvent(new NewEmailEvent(this, entity));

                    entity.setStatus(EmailEntity.ProcessingStatus.PUBLISHED);
                    emailRepository.save(entity);

                    newCount++;
                    log.info("Ingested and published event for message ID: {}", entity.getId());
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
