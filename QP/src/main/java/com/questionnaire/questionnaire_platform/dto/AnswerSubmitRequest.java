package com.questionnaire.questionnaire_platform.dto;

import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;

import java.util.UUID;

public record AnswerSubmitRequest(
        @NotNull(message = "questionId is required")
        UUID questionId,

        @NotNull(message = "value is required")
        JsonNode value
) {
}
