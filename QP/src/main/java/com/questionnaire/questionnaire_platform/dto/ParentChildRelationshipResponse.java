package com.questionnaire.questionnaire_platform.dto;

import com.questionnaire.questionnaire_platform.entity.ParentChildRelationship;

import java.time.LocalDateTime;
import java.util.UUID;

public record ParentChildRelationshipResponse(
        UUID id,
        UUID probandId,
        UUID parentPersonId,
        UUID parentFamilyMemberId,
        UUID childFamilyMemberId,
        LocalDateTime createdAt
) {
    public static ParentChildRelationshipResponse from(ParentChildRelationship relationship) {
        return new ParentChildRelationshipResponse(
                relationship.getId(),
                relationship.getProband().getId(),
                relationship.getParentPerson() == null ? null : relationship.getParentPerson().getId(),
                relationship.getParentFamilyMember() == null ? null : relationship.getParentFamilyMember().getId(),
                relationship.getChildFamilyMember().getId(),
                relationship.getCreatedAt()
        );
    }
}
