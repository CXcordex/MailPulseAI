package com.mailpulseai.monolith.ai;

import com.mailpulseai.monolith.entity.ProcessedEmailEntity;
import com.mailpulseai.monolith.repository.ProcessedEmailRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API that the frontend dashboard calls to display emails.
 *
 * All endpoints are routed through the API Gateway at http://localhost:8080/api/ai/...
 *
 * Endpoints:
 *   GET  /api/ai/emails            → paginated list (newest first)
 *   GET  /api/ai/emails?category=  → filter by category
 *   GET  /api/ai/emails/{id}       → single email with AI analysis
 *   GET  /api/ai/emails/stats      → category counts for donut chart
 *   PATCH /api/ai/emails/{id}/reply → edit draft reply from dashboard
 */
@RestController
@RequestMapping("/api/ai/emails")
@RequiredArgsConstructor
public class EmailDashboardController {

    private final ProcessedEmailRepository emailRepo;

    /**
     * Returns paginated email list, newest first.
     * Optional ?category=SPAM|IMPORTANT|URGENT|CLIENT|NEWSLETTER filter.
     */
    @GetMapping
    public ResponseEntity<List<ProcessedEmailEntity>> getEmails(
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("processedAt").descending());

        List<ProcessedEmailEntity> emails;
        if (category != null && !category.isBlank()) {
            try {
                ProcessedEmailEntity.EmailCategory cat =
                        ProcessedEmailEntity.EmailCategory.valueOf(category.toUpperCase());
                emails = emailRepo.findByCategory(cat, pageable).getContent();
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().build();
            }
        } else {
            emails = emailRepo.findAll(pageable).getContent();
        }
        return ResponseEntity.ok(emails);
    }

    /** Returns one email's full details including AI summary and draft reply. */
    @GetMapping("/{id}")
    public ResponseEntity<ProcessedEmailEntity> getEmail(@PathVariable String id) {
        return emailRepo.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Returns counts per category for the dashboard's donut chart.
     * Also includes total for the "AI Processed" stat card.
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> getStats() {
        Map<String, Long> stats = new HashMap<>();
        stats.put("IMPORTANT",  emailRepo.countByCategory(ProcessedEmailEntity.EmailCategory.IMPORTANT));
        stats.put("URGENT",     emailRepo.countByCategory(ProcessedEmailEntity.EmailCategory.URGENT));
        stats.put("SPAM",       emailRepo.countByCategory(ProcessedEmailEntity.EmailCategory.SPAM));
        stats.put("CLIENT",     emailRepo.countByCategory(ProcessedEmailEntity.EmailCategory.CLIENT));
        stats.put("NEWSLETTER", emailRepo.countByCategory(ProcessedEmailEntity.EmailCategory.NEWSLETTER));
        stats.put("TOTAL",      emailRepo.countAll());
        return ResponseEntity.ok(stats);
    }

    /** Lets the user edit the AI draft reply from the dashboard. */
    @PatchMapping("/{id}/reply")
    public ResponseEntity<Void> updateDraftReply(
            @PathVariable String id,
            @RequestBody Map<String, String> body
    ) {
        // BUG FIX: Use map() with explicit Void return to avoid raw ResponseEntity warning
        return emailRepo.findById(id).<ResponseEntity<Void>>map(email -> {
            String newDraft = body.get("draftReply");
            if (newDraft != null) {
                email.setDraftReply(newDraft);
                emailRepo.save(email);
            }
            return ResponseEntity.<Void>ok().build();
        }).orElse(ResponseEntity.<Void>notFound().build());
    }
}
