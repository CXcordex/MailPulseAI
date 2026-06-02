package com.mailpulseai.ai.repository;

import com.mailpulseai.ai.entity.ProcessedEmailEntity;
import com.mailpulseai.ai.entity.ProcessedEmailEntity.EmailCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the 'processed_emails' table.
 *
 * Powers the dashboard API:
 *   - List all emails with pagination/sorting
 *   - Filter by category (for the category tabs)
 *   - Count per category (for the donut chart)
 */
@Repository
public interface ProcessedEmailRepository extends JpaRepository<ProcessedEmailEntity, String> {

    /** Used by the dashboard to filter emails by category tab. */
    Page<ProcessedEmailEntity> findByCategory(EmailCategory category, Pageable pageable);

    /** Used by the stats endpoint to drive the donut chart. */
    long countByCategory(EmailCategory category);

    /** Returns the total count across all categories (for the "AI Processed" stat). */
    @Query("SELECT COUNT(e) FROM ProcessedEmailEntity e")
    long countAll();
}
