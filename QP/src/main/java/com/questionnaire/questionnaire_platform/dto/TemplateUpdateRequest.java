package com.questionnaire.questionnaire_platform.dto;

import com.questionnaire.questionnaire_platform.entity.SubjectScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;

// code is deliberately excluded - immutable after creation, same convention as Workflow.
public record TemplateUpdateRequest(
        @NotBlank(message = "name is required")
        String name,

        String description,

        @NotNull(message = "displayOrder is required")
        Integer displayOrder,

        JsonNode templateRule,

        @NotNull(message = "subjectScope is required")
        SubjectScope subjectScope
) {
}
