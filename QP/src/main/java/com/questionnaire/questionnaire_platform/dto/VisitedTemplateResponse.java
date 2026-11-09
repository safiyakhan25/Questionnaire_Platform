package com.questionnaire.questionnaire_platform.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record VisitedTemplateResponse(
        UUID templateId,
        String templateCode,
        String templateName,
        LocalDateTime firstAnsweredAt
) {
}
