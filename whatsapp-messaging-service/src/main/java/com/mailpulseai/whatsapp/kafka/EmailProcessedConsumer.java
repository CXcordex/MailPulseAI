package com.mailpulseai.whatsapp.kafka;

import com.mailpulseai.whatsapp.service.WhatsAppNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * CONSUMES from: 'email-processed'
 *
 * Stores email context in Redis (keyed by emailId) so the webhook
 * controller can look it up when the user replies YES/EDIT/IGNORE.
 *
 * Also stores the "pending" emailId per phone number so we know
 * which email the user is replying to (since Twilio only sends
 * the user's reply text, not the original notification content).
 *
 * Only sends WhatsApp notifications for URGENT, IMPORTANT, CLIENT emails.
 * SPAM and NEWSLETTER are silently dropped.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EmailProcessedConsumer {

    private static final Set<String> NOTIFY_CATEGORIES = Set.of("URGENT", "IMPORTANT", "CLIENT");
    public static final String REDIS_EMAIL_PREFIX   = "email:context:";
    public static final String REDIS_PENDING_PREFIX = "pending:whatsapp:";

    private final WhatsAppNotificationService whatsAppService;
    private final RedisTemplate<String, Object> redisTemplate;

    @KafkaListener(
        topics = "email-processed",
        groupId = "whatsapp-notification-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleProcessedEmail(Map<String, Object> event) {
        String emailId    = (String) event.get("emailId");
        String category   = (String) event.get("category");
        String subject    = (String) event.get("subject");
        String sender     = (String) event.get("senderName");
        String summary    = (String) event.get("summary");
        String draft      = (String) event.get("draftReply");
        String senderEmail = (String) event.get("senderEmail");

        log.info("WhatsApp service received email-id={} category={}", emailId, category);

        // Always store context in Redis (24h TTL) so webhook can look up email details
        if (emailId != null) {
            String redisKey = REDIS_EMAIL_PREFIX + emailId;
            redisTemplate.opsForHash().put(redisKey, "emailId",     emailId);
            redisTemplate.opsForHash().put(redisKey, "senderEmail", senderEmail != null ? senderEmail : "");
            redisTemplate.opsForHash().put(redisKey, "subject",     subject != null ? subject : "");
            redisTemplate.opsForHash().put(redisKey, "draftReply",  draft != null ? draft : "");
            redisTemplate.opsForHash().put(redisKey, "category",    category != null ? category : "");
            redisTemplate.expire(redisKey, 24, TimeUnit.HOURS);
            log.debug("Stored email context in Redis for emailId={}", emailId);
        }

        if (!NOTIFY_CATEGORIES.contains(category)) {
            log.info("Category={} — skipping WhatsApp notification.", category);
            return;
        }

        whatsAppService.sendEmailNotification(category, sender, subject, summary, draft, emailId);

        // After sending notification, store this emailId as the pending email
        // for the configured phone number. This is how the webhook knows
        // which email the user is replying to when they send YES/EDIT/IGNORE.
        // Key: pending:whatsapp:{toNumber} → emailId
        String toNumber = whatsAppService.getToNumber();
        if (toNumber != null && emailId != null) {
            redisTemplate.opsForValue().set(REDIS_PENDING_PREFIX + toNumber, emailId, 24, TimeUnit.HOURS);
            log.debug("Set pending email for {} → {}", toNumber, emailId);
        }
    }
}
