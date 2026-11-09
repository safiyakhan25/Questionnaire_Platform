package com.questionnaire.questionnaire_platform.repository;

import java.time.LocalDateTime;
import java.util.UUID;

// One row of FR-8.1's "actual answered path": a template the active subject has actually
// answered at least one question in, with when they first did so. Never computed from
// display_order or current rules - a skipped/never-visited template never appears here.
public record VisitedTemplateRow(UUID templateId, LocalDateTime firstAnsweredAt) {
}
