package com.mailpulseai.whatsapp.controller;

import com.mailpulseai.whatsapp.kafka.EmailProcessedConsumer;
import com.mailpulseai.whatsapp.kafka.ReplyApprovalPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Twilio calls this endpoint every time the user replies to a WhatsApp message.
 *
 * User replies are parsed and turned into Kafka events:
 *   "YES"          → reply-approved event with the AI draft
 *   "EDIT: <text>" → reply-approved event with custom text
 *   "IGNORE"       → no Kafka event, just log
 *
 * BUG FIX: @PostMapping now specifies produces = "text/xml" so Twilio receives
 * the response with the correct Content-Type and processes the TwiML response.
 * Without this, Spring defaults to application/json for String return type,
 * causing Twilio to ignore the response or show an error to the user.
 */
@RestController
@RequestMapping("/webhook/whatsapp")
@RequiredArgsConstructor
@Slf4j
public class WhatsAppWebhookController {

    private final ReplyApprovalPublisher approvalPublisher;
    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${twilio.to}")
    private String configuredToNumber;

    /**
     * Twilio sends form-encoded data when user replies.
     * Fields: From (user's number), To (our number), Body (user's message), MessageSid, etc.
     *
     * BUG FIX: produces = MediaType.TEXT_XML_VALUE ensures Twilio receives valid XML
     * Content-Type header so it can parse and act on the TwiML response.
     */
    @PostMapping(
        consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
        produces = MediaType.TEXT_XML_VALUE
    )
    public String receiveReply(@RequestParam Map<String, String> params) {
        String from   = params.get("From");   // user's WhatsApp number (whatsapp:+91...)
        String to     = params.get("To");     // our Twilio number (whatsapp:+14155238886)
        String body   = params.getOrDefault("Body", "").trim();
        String msgSid = params.get("MessageSid");

        log.info("WhatsApp reply received from={} body='{}' sid={}", from, body, msgSid);

        // Security check: restrict replies to the owner only
        if (from == null || !from.equalsIgnoreCase(configuredToNumber)) {
            log.warn("Ignored WhatsApp webhook request from unauthorized sender: {}", from);
            return twimlResponse("Unauthorized sender number.");
        }

        String replyUpper = body.toUpperCase();

        // Look up the pending emailId for the configured phone number.
        String emailId = lookupPendingEmailId(configuredToNumber);

        if (replyUpper.equals("YES")) {
            if (emailId == null) {
                log.warn("No pending email found for number={}", configuredToNumber);
                return twimlResponse("No pending email found. Please wait for a new notification.");
            }
            approvalPublisher.publishReplyApproved(emailId, null);
            clearPendingEmailId(configuredToNumber);
            return twimlResponse("Got it! Sending the AI draft reply now. ✅");

        } else if (replyUpper.startsWith("EDIT:")) {
            String customText = body.substring(5).trim();
            if (customText.isBlank()) {
                return twimlResponse("Please provide your reply text after EDIT: ");
            }
            if (emailId == null) {
                log.warn("No pending email found for number={}", configuredToNumber);
                return twimlResponse("No pending email found. Please wait for a new notification.");
            }
            approvalPublisher.publishReplyApproved(emailId, customText);
            clearPendingEmailId(configuredToNumber);
            return twimlResponse("Perfect! Sending your custom reply now. ✏️");

        } else if (replyUpper.equals("IGNORE")) {
            log.info("User chose to ignore email. emailId={}", emailId);
            clearPendingEmailId(configuredToNumber);
            return twimlResponse("Noted. Email archived. 🗂");

        } else {
            return twimlResponse("I didn't understand that. Reply YES, EDIT: <text>, or IGNORE.");
        }
    }

    private String lookupPendingEmailId(String phoneNumber) {
        try {
            Object value = redisTemplate.opsForValue().get(EmailProcessedConsumer.REDIS_PENDING_PREFIX + phoneNumber);
            return value != null ? value.toString() : null;
        } catch (Exception e) {
            log.error("Failed to look up pending emailId from Redis for number={}: {}", phoneNumber, e.getMessage());
            return null;
        }
    }

    private void clearPendingEmailId(String phoneNumber) {
        try {
            redisTemplate.delete(EmailProcessedConsumer.REDIS_PENDING_PREFIX + phoneNumber);
        } catch (Exception e) {
            log.warn("Failed to clear pending emailId from Redis: {}", e.getMessage());
        }
    }

    private String twimlResponse(String message) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Response><Message>" + message + "</Message></Response>";
    }
}
