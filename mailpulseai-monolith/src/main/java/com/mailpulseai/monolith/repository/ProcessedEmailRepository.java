package com.mailpulseai.monolith.repository;

import com.mailpulseai.monolith.entity.ProcessedEmailEntity;
import com.mailpulseai.monolith.entity.ProcessedEmailEntity.EmailCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcessedEmailRepository extends JpaRepository<ProcessedEmailEntity, String> {
    Page<ProcessedEmailEntity> findByCategory(EmailCategory category, Pageable pageable);
    
    long countByCategory(EmailCategory category);

    @Query("SELECT COUNT(p) FROM ProcessedEmailEntity p")
    long countAll();
}
