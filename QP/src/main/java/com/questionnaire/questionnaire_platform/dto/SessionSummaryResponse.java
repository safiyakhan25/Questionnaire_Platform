package com.questionnaire.questionnaire_platform.dto;

import com.questionnaire.questionnaire_platform.entity.SessionStatus;

import java.time.LocalDateTime;
import java.util.UUID;

// One row per QuestionnaireSession for the landing page's session list. displayName is the
// proband's name once answered, or a placeholder until then - see AssessmentService.listSessions().
public record SessionSummaryResponse(
        UUID sessionId,
        String displayName,
        SessionStatus status,
        LocalDateTime startedAt
) {
}
