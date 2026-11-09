package com.questionnaire.questionnaire_platform.dto;

import com.questionnaire.questionnaire_platform.entity.FamilyMemberRole;
import com.questionnaire.questionnaire_platform.entity.RelationshipType;

import java.util.UUID;

// One relational label a family member holds, relative to one scope - a person can have more than
// one of these once recursive adding is allowed (see QP-Family-Relationship-Persistence-Design.md).
public record FamilyMemberRoleView(
        UUID scopeId,
        RelationshipType relationshipType
) {
    public static FamilyMemberRoleView from(FamilyMemberRole role) {
        UUID scopeId = role.getScopePerson() != null ? role.getScopePerson().getId() : role.getScopeFamilyMember().getId();
        return new FamilyMemberRoleView(scopeId, role.getRelationshipType());
    }
}
