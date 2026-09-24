package com.questionnaire.questionnaire_platform.dto;

import com.questionnaire.questionnaire_platform.entity.AnswerType;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.UUID;

public record QuestionResponseView(
        UUID id,
        UUID questionId,
        UUID templateId,
        String answerKey,
        AnswerType answerType,
        JsonNode answerValue,
        LocalDateTime createdAt
) {
}
