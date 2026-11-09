package com.questionnaire.questionnaire_platform.dto;

import com.questionnaire.questionnaire_platform.entity.Question;
import com.questionnaire.questionnaire_platform.entity.QuestionType;
import com.questionnaire.questionnaire_platform.util.JsonColumnMapper;
import tools.jackson.databind.JsonNode;

import java.util.UUID;

// Named QuestionDto, not QuestionResponse, to avoid colliding with the QuestionResponse
// entity (QUESTION_RESPONSE table - a submitted answer, an unrelated concept).
public record QuestionDto(
        UUID id,
        UUID templateId,
        String answerKey,
        String text,
        QuestionType type,
        Integer displayOrder,
        JsonNode options,
        JsonNode validation,
        JsonNode visibilityRule,
        JsonNode fieldConfig
) {
    public static QuestionDto from(Question question, JsonColumnMapper jsonColumnMapper) {
        return new QuestionDto(
                question.getId(),
                question.getTemplate().getId(),
                question.getAnswerKey(),
                question.getText(),
                question.getType(),
                question.getDisplayOrder(),
                jsonColumnMapper.read(question.getOptions()),
                jsonColumnMapper.read(question.getValidation()),
                jsonColumnMapper.read(question.getVisibilityRule()),
                jsonColumnMapper.read(question.getFieldConfig())
        );
    }
}
