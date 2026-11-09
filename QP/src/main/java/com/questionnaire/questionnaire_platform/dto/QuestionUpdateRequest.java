package com.questionnaire.questionnaire_platform.dto;

import com.questionnaire.questionnaire_platform.entity.QuestionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;

// answerKey is deliberately excluded - immutable after creation, same convention as
// Workflow.code and Template.code. Rules elsewhere reference a question by answerKey,
// so changing it after creation would silently break those references.
public record QuestionUpdateRequest(
        @NotBlank(message = "text is required")
        String text,

        @NotNull(message = "type is required")
        QuestionType type,

        @NotNull(message = "displayOrder is required")
        Integer displayOrder,

        JsonNode options,
        JsonNode validation,
        JsonNode visibilityRule,
        JsonNode fieldConfig
) {
}
