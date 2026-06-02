package com.mailpulseai.ingestion.util;

import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartBody;
import com.google.api.services.gmail.model.MessagePartHeader;
import com.mailpulseai.ingestion.entity.EmailEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for EmailParserUtil.
 * Verifies that Gmail API Message objects are correctly mapped to EmailEntity.
 */
class EmailParserUtilTest {

    private EmailParserUtil parserUtil;

    @BeforeEach
    void setUp() {
        parserUtil = new EmailParserUtil();
    }

    @Test
    void parse_extractsSubjectAndSenderFromHeaders() {
        Message message = buildMessage(
            List.of(
                header("From", "Prof. Koushik Dutta <koushik@example.com>"),
                header("Subject", "Research Paper Submission Deadline")
            ),
            "Please submit your research paper by Friday.",
            1714000000000L   // epoch millis
        );

        EmailEntity result = parserUtil.parse(message);

        assertThat(result.getSubject()).isEqualTo("Research Paper Submission Deadline");
        assertThat(result.getSenderEmail()).isEqualTo("koushik@example.com");
        assertThat(result.getSenderName()).isEqualTo("Prof. Koushik Dutta");
        assertThat(result.getBodyText()).contains("research paper");
        assertThat(result.getGmailMessageId()).isEqualTo("msg-123");
    }

    @Test
    void parse_handlesEmailAddressWithoutDisplayName() {
        Message message = buildMessage(
            List.of(
                header("From", "plain@example.com"),
                header("Subject", "Test")
            ),
            "Hello.",
            1714000000000L
        );

        EmailEntity result = parserUtil.parse(message);

        assertThat(result.getSenderEmail()).isEqualTo("plain@example.com");
        assertThat(result.getSenderName()).isEqualTo("plain@example.com");
    }

    @Test
    void parse_defaultsSubjectWhenMissing() {
        Message message = buildMessage(
            List.of(header("From", "test@example.com")),
            "No subject here.",
            1714000000000L
        );

        EmailEntity result = parserUtil.parse(message);

        assertThat(result.getSubject()).isEqualTo("(no subject)");
    }

    @Test
    void parse_setsReceivedAtFromInternalDate() {
        long epochMs = 1714000000000L;
        Message message = buildMessage(
            List.of(
                header("From", "test@example.com"),
                header("Subject", "Test")
            ),
            "Body text.",
            epochMs
        );

        EmailEntity result = parserUtil.parse(message);

        assertThat(result.getReceivedAt()).isNotNull();
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private Message buildMessage(List<MessagePartHeader> headers, String bodyText, long internalDate) {
        String encoded = Base64.getUrlEncoder()
            .encodeToString(bodyText.getBytes(StandardCharsets.UTF_8));

        MessagePartBody body = new MessagePartBody().setData(encoded);
        MessagePart payload = new MessagePart()
            .setMimeType("text/plain")
            .setHeaders(headers)
            .setBody(body);

        return new Message()
            .setId("msg-123")
            .setInternalDate(internalDate)
            .setPayload(payload);
    }

    private MessagePartHeader header(String name, String value) {
        return new MessagePartHeader().setName(name).setValue(value);
    }
}
