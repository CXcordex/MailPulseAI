package com.mailpulseai.whatsapp.service;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Sends WhatsApp messages via Twilio's API.
 *
 * How it works:
 * 1. We format the AI summary + draft reply into a clean WhatsApp message
 * 2. User receives it on their phone
 * 3. User replies "YES", "EDIT: <text>", or "IGNORE"
 * 4. Twilio forwards that reply to our webhook (WhatsApp Webhook Controller)
 * 5. Webhook looks up the pending emailId from Redis (stored when notification was sent)
 * 6. Webhook publishes a 'reply-approved' Kafka event
 * 7. Outbound Mail Service picks it up and sends the email
 */
@Service
@Slf4j
public class WhatsAppNotificationService {

    @Value("${twilio.account.sid}")   private String accountSid;
    @Value("${twilio.auth.token}")    private String authToken;
    @Value("${twilio.from}")          private String fromNumber;   // whatsapp:+14155238886
    @Value("${twilio.to}")            private String toNumber;     // whatsapp:+91XXXXXXXXXX

    @PostConstruct
    public void init() {
        Twilio.init(accountSid, authToken);
        log.info("Twilio WhatsApp service initialised. from={} to={}", fromNumber, toNumber);
    }

    /**
     * Returns the configured destination phone number.
     * Used by EmailProcessedConsumer to store pending emailId keyed by phone number.
     */
    public String getToNumber() {
        return toNumber;
    }

    /**
     * Sends a notification for an important/urgent email.
     * The message format is optimised for quick reading on a phone.
     *
     * NOTE: We no longer include "_ref: emailId_" in the message because
     * Twilio only delivers the user's reply text (not the original message).
     * Instead, we track the pending emailId in Redis keyed by phone number.
     */
    public void sendEmailNotification(
            String category,
            String senderName,
            String subject,
            String summary,
            String draftReply,
            String emailId
    ) {
        String emoji = switch (category) {
            case "URGENT"    -> "🔴";
            case "IMPORTANT" -> "🟡";
            case "CLIENT"    -> "🟢";
            default          -> "📩";
        };

        String body = String.format("""
            %s *MailPulseAI AI — %s Email*
            
            *From:* %s
            *Subject:* %s
            
            *AI Summary:*
            %s
            
            ─────────────────
            *Draft Reply:*
            %s
            ─────────────────
            Reply to this message:
            ✅ *YES* — send draft as-is
            ✏️ *EDIT: <your text>* — send custom reply
            ❌ *IGNORE* — do nothing
            """,
                emoji, category,
                senderName != null ? senderName : "Unknown",
                subject != null ? subject : "(no subject)",
                summary != null ? summary : "(no summary)",
                draftReply != null ? draftReply : "(no reply needed)"
        );

        try {
            Message message = Message.creator(
                    new PhoneNumber(toNumber),
                    new PhoneNumber(fromNumber),
                    body
            ).create();

            log.info("WhatsApp notification sent for emailId={}. SID: {}", emailId, message.getSid());
        } catch (Exception e) {
            log.error("Failed to send WhatsApp notification for emailId={}: {}", emailId, e.getMessage(), e);
        }
    }
}
