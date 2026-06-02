package com.mailpulseai.whatsapp.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Publishes to: 'reply-approved'
 *
 * Called by the WhatsApp Webhook Controller when the user replies YES or EDIT.
 * Looks up the full email context from Redis so OutboundMailService knows
 * who to send the reply to and what the original subject was.
 *
 * BUG FIX: Removed incorrect JSON-quote unwrapping logic. Since both this service
 * and the ai-processing-service now use StringRedisSerializer for hash values,
 * values come back as plain strings — no unwrapping needed.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReplyApprovalPublisher {

    public static final String TOPIC_REPLY_APPROVED = "reply-approved";

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * @param emailId         the email to reply to
     * @param customReplyText null = use the AI draft; non-null = use this text instead
     */
    public void publishReplyApproved(String emailId, String customReplyText) {
        Map<String, Object> event = new HashMap<>();
        event.put("emailId",    emailId);
        event.put("useAiDraft", customReplyText == null ? "true" : "false");
        if (customReplyText != null) {
            event.put("customReplyText", customReplyText);
        }

        // Look up stored email context from Redis so OutboundMailService has all it needs
        try {
            Map<Object, Object> ctx = redisTemplate.opsForHash()
                    .entries(EmailProcessedConsumer.REDIS_EMAIL_PREFIX + emailId);
            if (ctx != null && !ctx.isEmpty()) {
                ctx.forEach((k, v) -> {
                    if (k != null && v != null) {
                        event.put(k.toString(), v.toString());
                    }
                });
                log.info("Loaded email context from Redis for emailId={}: to={}",
                        emailId, event.get("senderEmail"));
            } else {
                log.warn("No Redis context found for emailId={}. OutboundMailService may lack recipient info.", emailId);
            }
        } catch (Exception e) {
            log.error("Failed to load email context from Redis for emailId={}: {}", emailId, e.getMessage());
        }

        kafkaTemplate.send(TOPIC_REPLY_APPROVED, emailId, event);
        log.info("Published reply-approved for email-id={} useAiDraft={}", emailId, customReplyText == null);
    }
}
