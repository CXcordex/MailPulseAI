package com.mailpulseai.monolith.whatsapp;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class WhatsAppNotificationService {

    @Value("${twilio.account.sid}")   private String accountSid;
    @Value("${twilio.auth.token}")    private String authToken;
    @Value("${twilio.from}")          private String fromNumber;
    @Value("${twilio.to}")            private String toNumber;

    // In-memory mapping instead of Redis: phone_number -> pending_email_id
    private final ConcurrentHashMap<String, String> pendingEmails = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        Twilio.init(accountSid, authToken);
        log.info("Twilio WhatsApp service initialised. from={} to={}", fromNumber, toNumber);
    }

    public String getToNumber() {
        return toNumber;
    }

    public void setPendingEmail(String phone, String emailId) {
        pendingEmails.put(phone, emailId);
        log.debug("Stored pending email in memory: {} -> {}", phone, emailId);
    }

    public String getPendingEmail(String phone) {
        return pendingEmails.get(phone);
    }

    public void clearPendingEmail(String phone) {
        pendingEmails.remove(phone);
    }

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
