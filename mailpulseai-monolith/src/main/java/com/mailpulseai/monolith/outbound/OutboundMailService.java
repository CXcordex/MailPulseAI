package com.mailpulseai.monolith.outbound;

import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;
import com.mailpulseai.monolith.entity.ProcessedEmailEntity;
import com.mailpulseai.monolith.repository.ProcessedEmailRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.Properties;

/**
 * Sends approved email replies via the Gmail API.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OutboundMailService {

    private final Gmail gmailClient;
    private final ProcessedEmailRepository processedEmailRepository;
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

    public void sendApprovedReply(String emailId, String customReplyText) {
        ProcessedEmailEntity email = processedEmailRepository.findById(emailId).orElse(null);
        if (email == null) {
            log.error("No processed email entity found in DB for emailId={}", emailId);
            return;
        }
        String toEmail = email.getSenderEmail();
        String rawSubject = email.getSubject();
        String subject = rawSubject.toLowerCase().startsWith("re:") ? rawSubject : "Re: " + rawSubject;
        boolean useAiDraft = (customReplyText == null);
        String replyBody = useAiDraft ? email.getDraftReply() : customReplyText;

        if (toEmail == null || toEmail.isBlank()) {
            log.error("No recipient email found in database for emailId={}", emailId);
            return;
        }

        if (replyBody == null || replyBody.isBlank()) {
            log.warn("No reply text found for emailId={}. Skipping send.", emailId);
            return;
        }

        log.info("Sending approved reply for emailId={} to={}", emailId, toEmail);

        try {
            MimeMessage mimeMessage = buildEmail(toEmail, subject, replyBody);

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            mimeMessage.writeTo(buffer);
            String encodedEmail = Base64.encodeBase64URLSafeString(buffer.toByteArray());

            Message message = new Message();
            message.setRaw(encodedEmail);
            gmailClient.users().messages().send("me", message).execute();

            email.setReplyStatus(ProcessedEmailEntity.ReplyStatus.SENT);
            processedEmailRepository.save(email);
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
        email.setSubject(subject, "utf-8");
        email.setText(bodyText, "utf-8");
        return email;
    }
}
