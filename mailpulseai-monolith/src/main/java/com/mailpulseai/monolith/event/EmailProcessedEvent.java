package com.mailpulseai.monolith.event;

import com.mailpulseai.monolith.entity.ProcessedEmailEntity;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class EmailProcessedEvent extends ApplicationEvent {
    private final ProcessedEmailEntity processedEmail;

    public EmailProcessedEvent(Object source, ProcessedEmailEntity processedEmail) {
        super(source);
        this.processedEmail = processedEmail;
    }
}
