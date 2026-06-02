package com.mailpulseai.ingestion.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * JPA Entity persisted via Hibernate into the 'emails' table in PostgreSQL.
 *
 * BUG FIX: Replaced @Data with @Getter + @Setter + @NoArgsConstructor.
 * @Data on JPA entities generates equals/hashCode using all fields (breaks JPA identity)
 * and generates toString() that may trigger lazy loading causing LazyInitializationException.
 */
@Entity
@Table(name = "emails")
@Getter
@Setter
@NoArgsConstructor
public class EmailEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String gmailMessageId;   // Gmail's own message ID — used for dedup

    @Column(nullable = false)
    private String senderEmail;

    private String senderName;

    @Column(nullable = false)
    private String subject;

    @Column(columnDefinition = "TEXT")
    private String bodyText;          // plain-text body stripped from HTML

    @Column(nullable = false)
    private LocalDateTime receivedAt;

    @Enumerated(EnumType.STRING)
    private ProcessingStatus status;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EmailEntity)) return false;
        EmailEntity that = (EmailEntity) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "EmailEntity{id='" + id + "', gmailMessageId='" + gmailMessageId +
               "', subject='" + subject + "', status=" + status + "}";
    }

    public enum ProcessingStatus {
        RECEIVED,       // just pulled from Gmail
        PUBLISHED,      // Kafka event fired
        PROCESSED,      // AI service has handled it
        REPLIED         // outbound mail sent
    }
}
