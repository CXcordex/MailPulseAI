package com.mailpulseai.monolith.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "processed_emails")
@Data
public class ProcessedEmailEntity {

    @Id
    private String emailId; // Gmail message ID

    private String senderEmail;
    private String senderName;
    private String subject;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(columnDefinition = "TEXT")
    private String draftReply;

    private String classifyReason;
    
    private LocalDateTime processedAt = LocalDateTime.now();
    private LocalDateTime receivedAt;

    @Enumerated(EnumType.STRING)
    private EmailCategory category;

    private int importanceScore;

    @Enumerated(EnumType.STRING)
    private ReplyStatus replyStatus = ReplyStatus.PENDING_APPROVAL;

    public enum EmailCategory {
        SPAM,
        IMPORTANT,
        URGENT,
        NEWSLETTER,
        CLIENT
    }

    public enum ReplyStatus {
        PENDING_APPROVAL,
        SENT,
        IGNORED
    }
}
