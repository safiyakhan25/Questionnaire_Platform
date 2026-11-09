package com.questionnaire.questionnaire_platform.dto;

import com.questionnaire.questionnaire_platform.entity.MatingLineType;
import com.questionnaire.questionnaire_platform.entity.RelationshipStatusType;
import com.questionnaire.questionnaire_platform.entity.ReproductiveStatusType;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record PartnerRelationshipCreateRequest(
        @NotNull(message = "personAId is required")
        UUID personAId,

        @NotNull(message = "personBId is required")
        UUID personBId,

        // Defaults to MATING_LINE when omitted. Independent of relationshipStatus/consanguineous
        // below - this is the pedigree rendering code, not asked as a single conflated question.
        MatingLineType matingLineType,

        // Defaults to UNKNOWN when omitted.
        ReproductiveStatusType reproductiveStatus,

        // Required when reproductiveStatus = HAS_CHILDREN_NOT_SHOWN.
        Integer unshownChildrenCount,

        // Marital/separation history - independently askable, decoupled from consanguinity.
        RelationshipStatusType relationshipStatus,

        // Independently askable, decoupled from relationshipStatus/matingLineType.
        Boolean consanguineous,

        // Required when consanguineous = true (not matingLineType = CONSANGUINEOUS anymore).
        String consanguinityDegree,

        // Only valid when matingLineType is SEPARATED, DIVORCED, or NO_LONGER_TOGETHER.
        Integer endedYear
) {
}
