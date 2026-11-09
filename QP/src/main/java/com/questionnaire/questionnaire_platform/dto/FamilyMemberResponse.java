package com.questionnaire.questionnaire_platform.dto;

import com.questionnaire.questionnaire_platform.entity.FamilyMember;
import com.questionnaire.questionnaire_platform.entity.GenderIdentityType;
import com.questionnaire.questionnaire_platform.entity.MaternalPaternalSideType;
import com.questionnaire.questionnaire_platform.entity.SexAssignedAtBirthType;
import com.questionnaire.questionnaire_platform.util.JsonColumnMapper;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record FamilyMemberResponse(
        UUID id,
        UUID probandId,
        List<FamilyMemberRoleView> roles,
        MaternalPaternalSideType maternalPaternalSide,
        String firstName,
        String middleName,
        String lastName,
        String preferredName,
        SexAssignedAtBirthType sexAssignedAtBirth,
        GenderIdentityType genderIdentity,
        Boolean isIntersex,
        String[] countryOfOrigin,
        LocalDateTime birthDate,
        String maritalStatus,
        String communicationLanguage,
        String[] race,
        String[] ethnicity,
        String bloodType,
        Boolean deceased,
        LocalDateTime deceasedDateTime,
        Integer age,
        String primaryContact,
        JsonNode contact,
        JsonNode address,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static FamilyMemberResponse from(FamilyMember familyMember, List<FamilyMemberRoleView> roles,
                                             JsonColumnMapper jsonColumnMapper) {
        return new FamilyMemberResponse(
                familyMember.getId(),
                familyMember.getProband().getId(),
                roles,
                familyMember.getMaternalPaternalSide(),
                familyMember.getFirstName(),
                familyMember.getMiddleName(),
                familyMember.getLastName(),
                familyMember.getPreferredName(),
                familyMember.getSexAssignedAtBirth(),
                familyMember.getGenderIdentity(),
                familyMember.getIsIntersex(),
                familyMember.getCountryOfOrigin(),
                familyMember.getBirthDate(),
                familyMember.getMaritalStatus(),
                familyMember.getCommunicationLanguage(),
                familyMember.getRace(),
                familyMember.getEthnicity(),
                familyMember.getBloodType(),
                familyMember.getDeceased(),
                familyMember.getDeceasedDateTime(),
                familyMember.getAge(),
                familyMember.getPrimaryContact(),
                jsonColumnMapper.read(familyMember.getContact()),
                jsonColumnMapper.read(familyMember.getAddress()),
                familyMember.getCreatedAt(),
                familyMember.getUpdatedAt()
        );
    }
}
