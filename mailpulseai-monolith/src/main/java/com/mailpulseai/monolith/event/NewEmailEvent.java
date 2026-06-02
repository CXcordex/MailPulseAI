package com.mailpulseai.monolith.event;

import com.mailpulseai.monolith.entity.EmailEntity;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class NewEmailEvent extends ApplicationEvent {
    private final EmailEntity email;

    public NewEmailEvent(Object source, EmailEntity email) {
        super(source);
        this.email = email;
    }
}
