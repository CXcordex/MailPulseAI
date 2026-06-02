package com.mailpulseai.ai.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * Stores the AI analysis results for each email.
 * This is what powers the dashboard — every classified email is in this table.
 *
 * BUG FIX: Replaced @Data with @Getter + @Setter + @NoArgsConstructor.
 * Using @Data on JPA entities is a well-known anti-pattern:
 *   1. @Data generates equals/hashCode using ALL fields, which breaks JPA identity semantics.
 *      Two detached entities with the same @Id are considered unequal if any other field differs.
 *   2. @Data generates toString() that accesses ALL fields, triggering lazy loading
 *      outside a transaction and causing LazyInitializationException in logs/debug contexts.
 */
@Entity
@Table(name = "processed_emails")
@Getter
@Setter
@NoArgsConstructor
public class ProcessedEmailEntity {

    @Id
    private String emailId;           // same ID as EmailEntity in ingestion service

    private String senderName;
    private String senderEmail;

    @Column(nullable = false)
    private String subject;

    @Enumerated(EnumType.STRING)
    private EmailCategory category;

    private int importanceScore;      // 0-100 from Claude

    private String classifyReason;    // one-line explanation from Claude

    @Column(columnDefinition = "TEXT")
    private String summary;           // bullet-point summary from Claude

    @Column(columnDefinition = "TEXT")
    private String draftReply;        // AI-generated reply (null for spam/newsletter)

    @Enumerated(EnumType.STRING)
    private ReplyStatus replyStatus;

    private LocalDateTime processedAt;
    private LocalDateTime repliedAt;
    private LocalDateTime receivedAt; // when the original email was received

    /**
     * JPA-safe equals and hashCode based solely on @Id.
     * Two entities with the same emailId are always equal, regardless of other fields.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProcessedEmailEntity)) return false;
        ProcessedEmailEntity that = (ProcessedEmailEntity) o;
        return emailId != null && emailId.equals(that.emailId);
    }

    @Override
    public int hashCode() {
        return emailId != null ? emailId.hashCode() : 0;
    }

    @Override
    public String toString() {
        // Safe toString — does NOT access lazy collections
        return "ProcessedEmailEntity{emailId='" + emailId + "', category=" + category +
               ", subject='" + subject + "', score=" + importanceScore + "}";
    }

    public enum EmailCategory {
        IMPORTANT, URGENT, CLIENT, NEWSLETTER, SPAM
    }

    public enum ReplyStatus {
        PENDING_APPROVAL,  // waiting for WhatsApp YES/EDIT/IGNORE
        APPROVED,          // user said YES or EDIT
        SENT,              // outbound mail service sent it
        IGNORED            // user said IGNORE
    }
}
