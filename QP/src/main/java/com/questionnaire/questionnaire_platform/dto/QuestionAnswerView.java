package com.questionnaire.questionnaire_platform.dto;

import com.questionnaire.questionnaire_platform.entity.QuestionType;
import tools.jackson.databind.JsonNode;

import java.util.UUID;

// One question as rendered to the patient: its configuration, its current visible state
// (freshly evaluated against the proband's current facts), and any answer already on
// record for it in this session.
public record QuestionAnswerView(
        UUID id,
        String answerKey,
        String text,
        QuestionType type,
        Integer displayOrder,
        JsonNode options,
        JsonNode validation,
        boolean visible,
        JsonNode value
) {
}
