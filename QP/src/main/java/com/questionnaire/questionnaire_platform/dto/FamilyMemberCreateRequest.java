package com.questionnaire.questionnaire_platform.dto;

import com.questionnaire.questionnaire_platform.entity.MaternalPaternalSideType;
import com.questionnaire.questionnaire_platform.entity.RelationshipType;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

// Mirrors how PERSON itself is created at session start: a bare row with only what's known
// up front (here, how this relative relates to the proband), everything else filled in later
// one answer at a time via the same /answer endpoint PERSON already uses.
//
// scopeId is the anchor this relationshipType is relative to - the proband (default, when
// omitted) or any existing family member. Recursive adding: any family member can itself be a
// scope for adding further relatives, not just the proband. See
// QP-Family-Relationship-Persistence-Design.md. The resulting role is recorded in
// FAMILY_MEMBER_ROLE, not a column on FAMILY_MEMBER - the same person can hold more than one role
// relative to different scopes.
public record FamilyMemberCreateRequest(
        @NotNull(message = "relationshipType is required")
        RelationshipType relationshipType,

        @NotNull(message = "maternalPaternalSide is required")
        MaternalPaternalSideType maternalPaternalSide,

        UUID scopeId
) {
    public FamilyMemberCreateRequest(RelationshipType relationshipType, MaternalPaternalSideType maternalPaternalSide) {
        this(relationshipType, maternalPaternalSide, null);
    }
}
