package com.questionnaire.questionnaire_platform.dto;

import com.questionnaire.questionnaire_platform.entity.AnswerType;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record QuestionResponseView(
        UUID id,
        UUID questionId,
        UUID templateId,
        String answerKey,
        UUID familyMemberId,
        AnswerType answerType,
        JsonNode answerValue,
        LocalDateTime createdAt,
        // FR-9: answer_keys of downstream questions whose business-entity field this edit just
        // nullified (now-inapplicable template(s) or now-hidden question(s)). Empty on an ordinary,
        // non-cascading answer. The historical QUESTION_RESPONSE for each is untouched - this only
        // reports which current-state fields changed as a side effect.
        List<String> invalidatedAnswerKeys
) {
}
