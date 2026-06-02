package com.mailpulseai.ingestion.repository;

import com.mailpulseai.ingestion.entity.EmailEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the 'emails' table.
 * Spring auto-generates the implementation at startup — no SQL needed.
 *
 * existsByGmailMessageId is used for deduplication: before processing any
 * Gmail message we check if we've already stored it to avoid re-publishing
 * Kafka events across polling cycles.
 */
@Repository
public interface EmailRepository extends JpaRepository<EmailEntity, String> {

    /**
     * Checks if an email with the given Gmail message ID already exists.
     * Used by GmailPollingService to skip already-processed messages.
     */
    boolean existsByGmailMessageId(String gmailMessageId);
}
