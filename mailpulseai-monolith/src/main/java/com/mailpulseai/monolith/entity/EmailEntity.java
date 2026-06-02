package com.mailpulseai.monolith.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

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
    private String gmailMessageId;

    @Column(nullable = false)
    private String senderEmail;

    private String senderName;

    @Column(nullable = false)
    private String subject;

    @Column(columnDefinition = "TEXT")
    private String bodyText;

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
        RECEIVED,
        PUBLISHED,
        PROCESSED,
        REPLIED
    }
}
