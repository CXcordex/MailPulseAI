package com.mailpulseai.monolith.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class ReplyApprovedEvent extends ApplicationEvent {
    private final String emailId;
    private final String customReplyText;

    public ReplyApprovedEvent(Object source, String emailId, String customReplyText) {
        super(source);
        this.emailId = emailId;
        this.customReplyText = customReplyText;
    }
}
