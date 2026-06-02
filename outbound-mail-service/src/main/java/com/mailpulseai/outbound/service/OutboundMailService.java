package com.mailpulseai.outbound.service;

import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.Properties;

/**
 * Sends approved email replies via the Gmail API.
 *
 * Called by ReplyApprovedConsumer when the 'reply-approved' Kafka event arrives.
 * The event contains recipient, subject, and the approved reply text.
 *
 * BUG FIX (init): Removed @PostConstruct for Gmail profile resolution.
 * Making a network call during bean initialization causes startup failure if Gmail
 * API is temporarily unreachable (e.g. transient DNS or OAuth token refresh delay).
 * The email address is now resolved lazily on first send, with a fallback to "me".
 *
 * BUG FIX (subject): Avoids "Re: Re: Subject" by checking if prefix already exists.
 * BUG FIX (charset): Uses UTF-8 explicitly in setText() to support non-ASCII characters.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OutboundMailService {

    private final Gmail gmailClient;
    private volatile String authenticatedUserEmail; // lazy-resolved on first send

    /**
     * Resolves the Gmail address once and caches it.
     * Uses "me" as a safe fallback if the API call fails.
     */
    private String getAuthenticatedEmail() {
        if (authenticatedUserEmail == null) {
            synchronized (this) {
                if (authenticatedUserEmail == null) {
                    try {
                        authenticatedUserEmail = gmailClient.users()
                                .getProfile("me").execute().getEmailAddress();
                        log.info("Resolved Gmail sender address: {}", authenticatedUserEmail);
                    } catch (Exception e) {
                        log.warn("Could not resolve Gmail sender address (will use 'me'): {}", e.getMessage());
                        authenticatedUserEmail = "me";
                    }
                }
            }
        }
        return authenticatedUserEmail;
    }

    public void sendApprovedReply(Map<String, String> event) {
        String emailId     = event.get("emailId");
        String toEmail     = event.get("senderEmail");

        // BUG FIX: Avoid "Re: Re: Subject" — only prepend "Re: " if not already present
        String rawSubject  = event.getOrDefault("subject", "");
        String subject     = rawSubject.toLowerCase().startsWith("re:") ? rawSubject : "Re: " + rawSubject;

        // BUG FIX: useAiDraft could be stored as "true"/"false" string
        String useAiDraftStr = event.getOrDefault("useAiDraft", "true");
        boolean useAiDraft = Boolean.parseBoolean(useAiDraftStr);
        String replyBody   = useAiDraft
            ? event.get("draftReply")
            : event.get("customReplyText");

        if (toEmail == null || toEmail.isBlank()) {
            log.error("No recipient email found in reply-approved event for emailId={}. " +
                      "Check that Redis context was stored correctly by WhatsApp service.", emailId);
            return;
        }

        if (replyBody == null || replyBody.isBlank()) {
            log.warn("No reply text found for email-id={} (useAiDraft={}). " +
                     "draftReply='{}' customReplyText='{}'. Skipping send.",
                     emailId, useAiDraft, event.get("draftReply"), event.get("customReplyText"));
            return;
        }

        log.info("Sending approved reply for email-id={} to={}", emailId, toEmail);

        try {
            MimeMessage mimeMessage = buildEmail(toEmail, subject, replyBody);

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            mimeMessage.writeTo(buffer);
            String encodedEmail = Base64.encodeBase64URLSafeString(buffer.toByteArray());

            Message message = new Message();
            message.setRaw(encodedEmail);
            gmailClient.users().messages().send("me", message).execute();

            log.info("Email reply sent successfully for email-id={}", emailId);

        } catch (Exception e) {
            log.error("Failed to send reply for email-id={}: {}", emailId, e.getMessage(), e);
        }
    }

    private MimeMessage buildEmail(String to, String subject, String bodyText)
            throws MessagingException {
        Properties props = new Properties();
        Session session = Session.getDefaultInstance(props, null);

        MimeMessage email = new MimeMessage(session);
        email.setFrom(new InternetAddress(getAuthenticatedEmail()));
        email.addRecipient(jakarta.mail.Message.RecipientType.TO, new InternetAddress(to));
        email.setSubject(subject, "utf-8");          // BUG FIX: specify charset for subject
        email.setText(bodyText, "utf-8");             // BUG FIX: specify charset for body
        return email;
    }
}
