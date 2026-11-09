package com.questionnaire.questionnaire_platform.dto;

import java.util.List;
import java.util.UUID;

// Shared shape returned by start, current-template, next-template, and family-member-pass
// start/resume: the template currently in front of the current subject (familyMemberId null =
// the proband, set = that family member's pass is active), or completed=true with no template
// once the proband's own walk has no further applicable template.
public record CurrentTemplateResponse(
        UUID sessionId,
        UUID workflowId,
        UUID templateId,
        String templateCode,
        String templateName,
        UUID familyMemberId,
        List<QuestionAnswerView> questions,
        boolean completed
) {
}
