package com.mailpulseai.monolith.ingestion;

import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartHeader;
import com.mailpulseai.monolith.entity.EmailEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Parses a raw Gmail API {@link Message} into our {@link EmailEntity}.
 *
 * Gmail messages arrive with headers (From, Subject, Date) in a flat list,
 * and the body is Base64url-encoded across potentially nested MIME parts.
 * This utility handles both single-part (text/plain) and multi-part messages.
 *
 * BUG FIX: Added null checks for message payload and headers to prevent NPE
 * on malformed or partially-fetched Gmail messages.
 */
@Component
@Slf4j
public class EmailParserUtil {

    /**
     * Converts a full Gmail API Message into an EmailEntity ready to persist.
     *
     * @param message full Gmail message (fetched with format=full)
     * @return populated EmailEntity (id/status not set — caller sets those)
     */
    public EmailEntity parse(Message message) {
        EmailEntity entity = new EmailEntity();
        entity.setGmailMessageId(message.getId());

        // BUG FIX: Guard against null payload (malformed/empty Gmail message)
        MessagePart payload = message.getPayload();
        List<MessagePartHeader> headers = (payload != null && payload.getHeaders() != null)
                ? payload.getHeaders()
                : Collections.emptyList();

        // Extract standard email headers
        entity.setSenderEmail(extractAddress(getHeader(headers, "From")));
        entity.setSenderName(extractName(getHeader(headers, "From")));
        entity.setSubject(getHeader(headers, "Subject").orElse("(no subject)"));

        // Gmail internalDate is epoch millis
        if (message.getInternalDate() != null) {
            entity.setReceivedAt(
                LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(message.getInternalDate()),
                    ZoneId.systemDefault()
                )
            );
        } else {
            entity.setReceivedAt(LocalDateTime.now());
        }

        // Extract plain-text body (null payload → empty body)
        String body = (payload != null) ? extractBody(payload) : null;
        entity.setBodyText(body != null ? body : "");

        return entity;
    }

    // ── Private helpers ──────────────────────────────────────────────

    private Optional<String> getHeader(List<MessagePartHeader> headers, String name) {
        if (headers == null || headers.isEmpty()) return Optional.empty();
        return headers.stream()
            .filter(h -> h.getName().equalsIgnoreCase(name))
            .map(MessagePartHeader::getValue)
            .findFirst();
    }

    /**
     * Extracts email address from "Name <email@example.com>" format.
     */
    private String extractAddress(Optional<String> from) {
        return from.map(f -> {
            int start = f.indexOf('<');
            int end   = f.indexOf('>');
            if (start >= 0 && end > start) {
                return f.substring(start + 1, end).trim();
            }
            return f.trim();
        }).orElse("unknown@unknown.com");
    }

    /**
     * Extracts display name from "Name <email@example.com>" format.
     * Returns the email address if no name is present.
     */
    private String extractName(Optional<String> from) {
        return from.map(f -> {
            int start = f.indexOf('<');
            if (start > 0) {
                return f.substring(0, start).trim().replace("\"", "");
            }
            return f.trim();
        }).orElse("Unknown Sender");
    }

    /**
     * Recursively walks the MIME tree to find the plain-text body.
     * Prefers text/plain; falls back to stripping HTML tags from text/html.
     */
    private String extractBody(MessagePart part) {
        if (part == null) return null;

        String mimeType = part.getMimeType();

        // Single text/plain part
        if ("text/plain".equalsIgnoreCase(mimeType) && part.getBody() != null
                && part.getBody().getData() != null) {
            return decodeBase64(part.getBody().getData());
        }

        // Prefer text/plain but fall back to text/html
        if (part.getParts() != null) {
            String plainText = null;
            String htmlText  = null;
            for (MessagePart child : part.getParts()) {
                String childMime = child.getMimeType();
                if ("text/plain".equalsIgnoreCase(childMime) && child.getBody() != null
                        && child.getBody().getData() != null) {
                    plainText = decodeBase64(child.getBody().getData());
                } else if ("text/html".equalsIgnoreCase(childMime) && child.getBody() != null
                        && child.getBody().getData() != null) {
                    htmlText = stripHtml(decodeBase64(child.getBody().getData()));
                } else {
                    // recurse into nested multi-part
                    String nested = extractBody(child);
                    if (nested != null && plainText == null) {
                        plainText = nested;
                    }
                }
            }
            return plainText != null ? plainText : htmlText;
        }

        return null;
    }

    private String decodeBase64(String encoded) {
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(encoded);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("Failed to decode base64 email body: {}", e.getMessage());
            return "";
        }
    }

    private String stripHtml(String html) {
        if (html == null) return "";
        // Simple HTML tag stripping — sufficient for email body preview
        return html.replaceAll("<[^>]+>", " ")
                   .replaceAll("&nbsp;", " ")
                   .replaceAll("&amp;", "&")
                   .replaceAll("&lt;", "<")
                   .replaceAll("&gt;", ">")
                   .replaceAll("\\s{2,}", " ")
                   .trim();
    }
}
