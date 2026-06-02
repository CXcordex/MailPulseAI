package com.mailpulseai.monolith.whatsapp;

import com.mailpulseai.monolith.event.ReplyApprovedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/webhook/whatsapp")
@RequiredArgsConstructor
@Slf4j
public class WhatsAppWebhookController {

    private final WhatsAppNotificationService whatsAppService;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${twilio.to}")
    private String configuredToNumber;

    @PostMapping(
        consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
        produces = MediaType.TEXT_XML_VALUE
    )
    public String receiveReply(@RequestParam Map<String, String> params) {
        String from   = params.get("From");
        String to     = params.get("To");
        String body   = params.getOrDefault("Body", "").trim();
        String msgSid = params.get("MessageSid");

        log.info("WhatsApp reply received from={} body='{}' sid={}", from, body, msgSid);

        if (from == null || !from.equalsIgnoreCase(configuredToNumber)) {
            log.warn("Ignored WhatsApp webhook request from unauthorized sender: {}", from);
            return twimlResponse("Unauthorized sender number.");
        }

        String replyUpper = body.toUpperCase();
        String emailId = whatsAppService.getPendingEmail(configuredToNumber);

        if (replyUpper.equals("YES")) {
            if (emailId == null) {
                return twimlResponse("No pending email found. Please wait for a new notification.");
            }
            // Trigger local event
            eventPublisher.publishEvent(new ReplyApprovedEvent(this, emailId, null));
            whatsAppService.clearPendingEmail(configuredToNumber);
            return twimlResponse("Got it! Sending the AI draft reply now. ✅");

        } else if (replyUpper.startsWith("EDIT:")) {
            String customText = body.substring(5).trim();
            if (customText.isBlank()) {
                return twimlResponse("Please provide your reply text after EDIT: ");
            }
            if (emailId == null) {
                return twimlResponse("No pending email found. Please wait for a new notification.");
            }
            // Trigger local event
            eventPublisher.publishEvent(new ReplyApprovedEvent(this, emailId, customText));
            whatsAppService.clearPendingEmail(configuredToNumber);
            return twimlResponse("Perfect! Sending your custom reply now. ✏️");

        } else if (replyUpper.equals("IGNORE")) {
            log.info("User chose to ignore email. emailId={}", emailId);
            whatsAppService.clearPendingEmail(configuredToNumber);
            return twimlResponse("Noted. Email archived. 🗂");

        } else {
            return twimlResponse("I didn't understand that. Reply YES, EDIT: <text>, or IGNORE.");
        }
    }

    private String twimlResponse(String message) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Response><Message>" + message + "</Message></Response>";
    }
}
