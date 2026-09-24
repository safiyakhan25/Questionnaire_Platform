package com.questionnaire.questionnaire_platform.dto;

import com.questionnaire.questionnaire_platform.entity.Template;
import com.questionnaire.questionnaire_platform.util.JsonColumnMapper;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.UUID;

public record TemplateResponse(
        UUID id,
        UUID workflowId,
        String code,
        String name,
        String description,
        Integer displayOrder,
        JsonNode templateRule,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static TemplateResponse from(Template template, JsonColumnMapper jsonColumnMapper) {
        return new TemplateResponse(
                template.getId(),
                template.getWorkflow().getId(),
                template.getCode(),
                template.getName(),
                template.getDescription(),
                template.getDisplayOrder(),
                jsonColumnMapper.read(template.getTemplateRule()),
                template.getCreatedAt(),
                template.getUpdatedAt()
        );
    }
}
