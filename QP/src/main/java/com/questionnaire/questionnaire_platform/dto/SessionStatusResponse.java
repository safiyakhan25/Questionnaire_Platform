package com.questionnaire.questionnaire_platform.dto;

import com.questionnaire.questionnaire_platform.entity.SessionStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record SessionStatusResponse(
        UUID sessionId,
        SessionStatus status,
        LocalDateTime startedAt,
        LocalDateTime completedAt
) {
}
