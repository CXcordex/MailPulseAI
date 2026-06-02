package com.mailpulseai.monolith.repository;

import com.mailpulseai.monolith.entity.EmailEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EmailRepository extends JpaRepository<EmailEntity, String> {
    boolean existsByGmailMessageId(String gmailMessageId);
    
    // Fallback if schema uses default field name
    default boolean existsByGmailMessageIdFallback(String msgId) {
        return existsById(msgId);
    }
}
