package com.mailpulseai.monolith.outbound;

import com.mailpulseai.monolith.event.ReplyApprovedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReplyApprovedListener {

    private final OutboundMailService outboundMailService;

    @Async
    @EventListener
    public void handleReplyApproved(ReplyApprovedEvent event) {
        log.info("Outbound Listener received ReplyApprovedEvent for emailId={}", event.getEmailId());
        outboundMailService.sendApprovedReply(event.getEmailId(), event.getCustomReplyText());
    }
}
