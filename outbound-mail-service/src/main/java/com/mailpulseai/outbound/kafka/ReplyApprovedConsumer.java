package com.mailpulseai.outbound.kafka;

import com.mailpulseai.outbound.service.OutboundMailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Consumes 'reply-approved' events from Kafka.
 *
 * The event contains either:
 *   useAiDraft=true  → look up the stored AI draft reply and send it
 *   useAiDraft=false → use the customReplyText field directly
 *
 * We store the full email context (toEmail, subject, body) in the event
 * because the outbound service is stateless and doesn't share a DB.
 *
 * NOTE: The Kafka value is Map<String,Object> (published by ReplyApprovalPublisher),
 * so we receive it as such and convert values to String for the mail service.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReplyApprovedConsumer {

    private final OutboundMailService outboundMailService;

    @KafkaListener(
        topics = "reply-approved",
        groupId = "outbound-mail-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleReplyApproved(Map<String, Object> event) {
        log.info("Received reply-approved event for emailId={}", event.get("emailId"));

        // Convert Map<String, Object> to Map<String, String> for OutboundMailService
        Map<String, String> stringEvent = new java.util.HashMap<>();
        event.forEach((k, v) -> {
            if (k != null && v != null) {
                stringEvent.put(k, v.toString());
            }
        });

        outboundMailService.sendApprovedReply(stringEvent);
    }
}
