package com.mailpulseai.ai.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for AIEmailProcessorService.
 * (ChatModel mocking removed as the service now uses native HttpClient).
 */
@ExtendWith(MockitoExtension.class)
class AIEmailProcessorServiceTest {

    private AIEmailProcessorService service;

    @BeforeEach
    void setUp() {
        service = new AIEmailProcessorService();
        service.initProviders();
    }

    @Test
    void draftReply_returnsNullForSpam() {
        String result = service.draftReply("FREE MONEY", "Click here!!!", "SPAM");
        assertThat(result).isNull();
    }

    @Test
    void draftReply_returnsNullForNewsletter() {
        String result = service.draftReply("Weekly digest", "Your weekly update...", "NEWSLETTER");
        assertThat(result).isNull();
    }
}
