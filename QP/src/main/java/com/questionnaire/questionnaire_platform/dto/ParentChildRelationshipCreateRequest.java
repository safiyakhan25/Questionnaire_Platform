package com.questionnaire.questionnaire_platform.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ParentChildRelationshipCreateRequest(
        @NotNull(message = "parentId is required")
        UUID parentId,

        @NotNull(message = "childFamilyMemberId is required")
        UUID childFamilyMemberId
) {
}
