package com.questionnaire.questionnaire_platform.dto;

import com.questionnaire.questionnaire_platform.entity.QuestionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;

public record QuestionCreateRequest(
        @NotBlank(message = "answerKey is required")
        String answerKey,

        @NotBlank(message = "text is required")
        String text,

        @NotNull(message = "type is required")
        QuestionType type,

        @NotNull(message = "displayOrder is required")
        Integer displayOrder,

        JsonNode options,
        JsonNode validation,
        JsonNode visibilityRule
) {
}
