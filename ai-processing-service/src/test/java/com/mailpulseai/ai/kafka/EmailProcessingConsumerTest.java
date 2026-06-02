package com.mailpulseai.ai.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mailpulseai.ai.service.AIEmailProcessorService;
import com.mailpulseai.ai.service.EmailProcessedEventService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit test for EmailProcessingConsumer.
 * Tests the routing/orchestration logic without real Kafka or Claude.
 */
@ExtendWith(MockitoExtension.class)
class EmailProcessingConsumerTest {

    @Mock
    private AIEmailProcessorService aiService;

    @Mock
    private EmailProcessedEventService persistService;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private EmailProcessingConsumer consumer;

    @Test
    void handleNewEmail_classifiesAndPublishesEvent() throws Exception {
        // Arrange
        Map<String, Object> incomingEvent = new HashMap<>();
        incomingEvent.put("emailId", "email-001");
        incomingEvent.put("subject", "Research Paper Submission Deadline");
        incomingEvent.put("bodyText", "Please submit by Friday 5pm.");
        incomingEvent.put("senderName", "Prof. Koushik Dutta");
        incomingEvent.put("senderEmail", "koushik@example.com");
        incomingEvent.put("receivedAt", "2026-05-04T09:14:00");

        String classifyJson = "{\"category\":\"URGENT\",\"score\":98,\"reason\":\"Deadline tomorrow\"}";
        Map<String, Object> classification = Map.of("category", "URGENT", "score", 98, "reason", "Deadline tomorrow");

        when(aiService.classify(anyString(), anyString())).thenReturn(classifyJson);
        when(objectMapper.readValue(classifyJson, Map.class)).thenReturn(classification);
        when(aiService.summarise(anyString(), anyString())).thenReturn("• Submit by Friday\n• Use portal");
        when(aiService.draftReply(anyString(), anyString(), eq("URGENT")))
            .thenReturn("Thank you for the reminder.");

        // Act
        consumer.handleNewEmail(incomingEvent);

        // Assert
        verify(aiService).classify("Research Paper Submission Deadline", "Please submit by Friday 5pm.");
        verify(aiService).summarise("Research Paper Submission Deadline", "Please submit by Friday 5pm.");
        verify(aiService).draftReply("Research Paper Submission Deadline", "Please submit by Friday 5pm.", "URGENT");
        verify(persistService).saveProcessedEmail(any());
        verify(kafkaTemplate).send(eq("email-processed"), eq("email-001"), any());
    }

    @Test
    void handleNewEmail_doesNotCrashOnException() {
        Map<String, Object> incomingEvent = new HashMap<>();
        incomingEvent.put("emailId", "email-002");
        incomingEvent.put("subject", "Test");
        incomingEvent.put("bodyText", "Test body");

        when(aiService.classify(anyString(), anyString()))
            .thenThrow(new RuntimeException("Claude API timeout"));

        // Should not throw — errors are caught and logged
        consumer.handleNewEmail(incomingEvent);

        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }
}
