package com.questionnaire.questionnaire_platform.dto;

import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;

import java.util.UUID;

public record AnswerSubmitRequest(
        @NotNull(message = "questionId is required")
        UUID questionId,

        @NotNull(message = "value is required")
        JsonNode value,

        // Null = this answer is about the proband (existing behavior). Set = the answer is
        // about this specific family member instead; must belong to the session's own proband.
        UUID familyMemberId
) {
}
