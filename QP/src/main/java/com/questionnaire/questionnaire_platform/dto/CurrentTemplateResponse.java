package com.questionnaire.questionnaire_platform.dto;

import java.util.List;
import java.util.UUID;

// Shared shape returned by start, current-template, and next-template: the template
// currently in front of the proband, or completed=true with no template once the
// questionnaire has no further applicable template.
public record CurrentTemplateResponse(
        UUID sessionId,
        UUID workflowId,
        UUID templateId,
        String templateCode,
        String templateName,
        List<QuestionAnswerView> questions,
        boolean completed
) {
}
