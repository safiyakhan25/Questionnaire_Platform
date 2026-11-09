package com.questionnaire.questionnaire_platform.dto;

import com.questionnaire.questionnaire_platform.entity.MatingLineType;
import com.questionnaire.questionnaire_platform.entity.PartnerRelationship;
import com.questionnaire.questionnaire_platform.entity.RelationshipStatusType;
import com.questionnaire.questionnaire_platform.entity.ReproductiveStatusType;

import java.time.LocalDateTime;
import java.util.UUID;

public record PartnerRelationshipResponse(
        UUID id,
        UUID probandId,
        UUID personAPersonId,
        UUID personAFamilyMemberId,
        UUID personBPersonId,
        UUID personBFamilyMemberId,
        MatingLineType matingLineType,
        ReproductiveStatusType reproductiveStatus,
        RelationshipStatusType relationshipStatus,
        Boolean consanguineous,
        Integer unshownChildrenCount,
        String consanguinityDegree,
        Integer endedYear,
        LocalDateTime createdAt
) {
    public static PartnerRelationshipResponse from(PartnerRelationship relationship) {
        return new PartnerRelationshipResponse(
                relationship.getId(),
                relationship.getProband().getId(),
                relationship.getPersonAPerson() == null ? null : relationship.getPersonAPerson().getId(),
                relationship.getPersonAFamilyMember() == null ? null : relationship.getPersonAFamilyMember().getId(),
                relationship.getPersonBPerson() == null ? null : relationship.getPersonBPerson().getId(),
                relationship.getPersonBFamilyMember() == null ? null : relationship.getPersonBFamilyMember().getId(),
                relationship.getMatingLineType(),
                relationship.getReproductiveStatus(),
                relationship.getRelationshipStatus(),
                relationship.getConsanguineous(),
                relationship.getUnshownChildrenCount(),
                relationship.getConsanguinityDegree(),
                relationship.getEndedYear(),
                relationship.getCreatedAt()
        );
    }
}
