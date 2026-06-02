package com.mailpulseai.ingestion.kafka;

import com.mailpulseai.ingestion.entity.EmailEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Publishes events to the 'new-email' Kafka topic.
 *
 * The message payload is a Map (serialized as JSON) that the AI Processing
 * Service reads. We send only the data the AI needs — not the full entity —
 * to keep the Kafka message lightweight.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EmailEventPublisher {

    public static final String TOPIC_NEW_EMAIL = "new-email";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishNewEmail(EmailEntity email) {
        Map<String, String> event = new HashMap<>();
        event.put("emailId", email.getId());
        event.put("senderName", email.getSenderName());
        event.put("senderEmail", email.getSenderEmail());
        event.put("subject", email.getSubject());
        event.put("bodyText", email.getBodyText());
        event.put("receivedAt", email.getReceivedAt().toString());

        // Key = emailId so all events for the same email go to the same partition
        kafkaTemplate.send(TOPIC_NEW_EMAIL, email.getId(), event);
        log.info("Published to topic '{}' for email '{}'", TOPIC_NEW_EMAIL, email.getId());
    }
}
