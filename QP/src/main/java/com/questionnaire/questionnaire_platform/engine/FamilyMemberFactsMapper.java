package com.questionnaire.questionnaire_platform.engine;

import com.questionnaire.questionnaire_platform.entity.FamilyMember;
import com.questionnaire.questionnaire_platform.util.JsonColumnMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Same shape as PersonFactsMapper, built from FAMILY_MEMBER instead of PERSON - used whenever the
// current subject of a template/question-visibility rule evaluation is a family member rather than
// the proband. relationship_type/maternal_paternal_side are deliberately not included: nothing a
// rule evaluates today needs them, and they're set once at creation, not answer-driven.
@Component
@RequiredArgsConstructor
public class FamilyMemberFactsMapper {

    private final JsonColumnMapper jsonColumnMapper;
    private final ObjectMapper objectMapper;

    public Map<String, Object> toFacts(FamilyMember familyMember) {
        Map<String, Object> facts = new HashMap<>();
        facts.put("first_name", familyMember.getFirstName());
        facts.put("middle_name", familyMember.getMiddleName());
        facts.put("last_name", familyMember.getLastName());
        facts.put("preferred_name", familyMember.getPreferredName());
        facts.put("sex_assigned_at_birth", familyMember.getSexAssignedAtBirth() == null ? null : familyMember.getSexAssignedAtBirth().name());
        facts.put("gender_identity", familyMember.getGenderIdentity() == null ? null : familyMember.getGenderIdentity().name());
        facts.put("is_intersex", familyMember.getIsIntersex());
        facts.put("country_of_origin", familyMember.getCountryOfOrigin());
        facts.put("birth_date", familyMember.getBirthDate() == null ? null : familyMember.getBirthDate().toString());
        facts.put("marital_status", familyMember.getMaritalStatus());
        facts.put("communication_language", familyMember.getCommunicationLanguage());
        facts.put("race", familyMember.getRace() == null ? null : List.of(familyMember.getRace()));
        facts.put("ethnicity", familyMember.getEthnicity() == null ? null : List.of(familyMember.getEthnicity()));
        facts.put("blood_type", familyMember.getBloodType());
        facts.put("deceased", familyMember.getDeceased());
        facts.put("deceased_date_time", familyMember.getDeceasedDateTime() == null ? null : familyMember.getDeceasedDateTime().toString());
        facts.put("age", familyMember.getAge());
        facts.put("primary_contact", familyMember.getPrimaryContact());
        facts.put("contact", toMap(jsonColumnMapper.read(familyMember.getContact())));
        facts.put("address", toMap(jsonColumnMapper.read(familyMember.getAddress())));

        Map<String, Object> additionalFacts = toMap(jsonColumnMapper.read(familyMember.getAdditionalFacts()));
        if (additionalFacts != null) {
            facts.putAll(additionalFacts);
        }

        return facts;
    }

    private Map<String, Object> toMap(JsonNode node) {
        if (node == null || !node.isObject()) {
            return null;
        }
        return objectMapper.convertValue(node, Map.class);
    }
}
