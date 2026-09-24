package com.questionnaire.questionnaire_platform.dto;

import java.util.UUID;

// sessionId is optional: omitted (or null) starts a brand-new session and PERSON; supplying
// an existing IN_PROGRESS session's id resumes it instead of creating a duplicate PERSON
// (FR-1). This is a deliberately simplified stand-in for real session identification
// (FR-16's signed cookie, FR-17's returning-patient matching) - both deferred to a later pass.
public record AssessmentStartRequest(
        UUID sessionId
) {
}
