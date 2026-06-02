package com.mailpulseai.monolith.whatsapp;

import com.mailpulseai.monolith.entity.ProcessedEmailEntity;
import com.mailpulseai.monolith.event.EmailProcessedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmailProcessedListener {

    private static final Set<String> NOTIFY_CATEGORIES = Set.of("URGENT", "IMPORTANT", "CLIENT");
    private final WhatsAppNotificationService whatsAppService;

    @Async
    @EventListener
    public void handleProcessedEmail(EmailProcessedEvent event) {
        ProcessedEmailEntity processed = event.getProcessedEmail();
        String emailId = processed.getEmailId();
        String category = processed.getCategory().name();
        String subject = processed.getSubject();
        String sender = processed.getSenderName();
        String summary = processed.getSummary();
        String draft = processed.getDraftReply();

        log.info("WhatsApp Listener received EmailProcessedEvent: emailId={} category={}", emailId, category);

        if (!NOTIFY_CATEGORIES.contains(category)) {
            log.info("Category={} — skipping WhatsApp notification.", category);
            return;
        }

        whatsAppService.sendEmailNotification(category, sender, subject, summary, draft, emailId);

        // Store phone number to emailId correlation in-memory
        String toNumber = whatsAppService.getToNumber();
        if (toNumber != null && emailId != null) {
            whatsAppService.setPendingEmail(toNumber, emailId);
        }
    }
}
