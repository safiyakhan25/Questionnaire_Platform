package com.questionnaire.questionnaire_platform.dto;

import com.questionnaire.questionnaire_platform.entity.SubjectScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;

public record TemplateCreateRequest(
        @NotBlank(message = "code is required")
        String code,

        @NotBlank(message = "name is required")
        String name,

        String description,

        @NotNull(message = "displayOrder is required")
        Integer displayOrder,

        JsonNode templateRule,

        // Defaults to PROBAND_ONLY when omitted, preserving today's behavior.
        SubjectScope subjectScope
) {
}
