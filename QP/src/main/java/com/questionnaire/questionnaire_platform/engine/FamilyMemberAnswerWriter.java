package com.questionnaire.questionnaire_platform.engine;

import com.questionnaire.questionnaire_platform.entity.FamilyMember;
import com.questionnaire.questionnaire_platform.entity.GenderIdentityType;
import com.questionnaire.questionnaire_platform.entity.SexAssignedAtBirthType;
import com.questionnaire.questionnaire_platform.exception.InvalidAnswerException;
import com.questionnaire.questionnaire_platform.util.JsonColumnMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

// Same answer_key -> column mapping as PersonAnswerWriter, applied to FAMILY_MEMBER instead
// of PERSON. relationship_type/maternal_paternal_side are set at creation time (FamilyMemberService),
// never through an answer, so they're deliberately not handled here.
@Component
@RequiredArgsConstructor
public class FamilyMemberAnswerWriter {

    private final JsonColumnMapper jsonColumnMapper;
    private final ObjectMapper objectMapper;

    public void apply(FamilyMember familyMember, String answerKey, JsonNode value) {
        switch (answerKey) {
            case "first_name" -> familyMember.setFirstName(text(value));
            case "middle_name" -> familyMember.setMiddleName(text(value));
            case "last_name" -> familyMember.setLastName(text(value));
            case "preferred_name" -> familyMember.setPreferredName(text(value));
            case "sex_assigned_at_birth" -> familyMember.setSexAssignedAtBirth(enumValue(answerKey, value, SexAssignedAtBirthType.class));
            case "gender_identity" -> familyMember.setGenderIdentity(enumValue(answerKey, value, GenderIdentityType.class));
            case "is_intersex" -> familyMember.setIsIntersex(value == null || value.isNull() ? null : value.asBoolean());
            case "country_of_origin" -> familyMember.setCountryOfOrigin(stringArray(value));
            case "birth_date" -> familyMember.setBirthDate(dateTime(answerKey, value));
            case "marital_status" -> familyMember.setMaritalStatus(text(value));
            case "communication_language" -> familyMember.setCommunicationLanguage(text(value));
            case "race" -> familyMember.setRace(stringArray(value));
            case "ethnicity" -> familyMember.setEthnicity(stringArray(value));
            case "blood_type" -> familyMember.setBloodType(text(value));
            case "deceased" -> familyMember.setDeceased(value == null || value.isNull() ? null : value.asBoolean());
            case "deceased_date_time" -> familyMember.setDeceasedDateTime(dateTime(answerKey, value));
            case "age" -> familyMember.setAge(value == null || value.isNull() ? null : value.asInt());
            case "primary_contact" -> familyMember.setPrimaryContact(text(value));
            case "contact" -> familyMember.setContact(jsonColumnMapper.write(value));
            case "address" -> familyMember.setAddress(jsonColumnMapper.write(value));
            default -> mergeIntoAdditionalFacts(familyMember, answerKey, value);
        }
    }

    private String text(JsonNode value) {
        return value == null || value.isNull() ? null : value.asString();
    }

    private String[] stringArray(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isArray()) {
            throw new InvalidAnswerException("Expected a JSON array of strings");
        }
        List<String> items = new ArrayList<>();
        value.forEach(item -> items.add(item.asString()));
        return items.toArray(new String[0]);
    }

    private <E extends Enum<E>> E enumValue(String answerKey, JsonNode value, Class<E> enumType) {
        if (value == null || value.isNull()) {
            return null;
        }
        try {
            return Enum.valueOf(enumType, value.asString());
        } catch (IllegalArgumentException e) {
            throw new InvalidAnswerException(
                    "'" + value.asString() + "' is not a valid value for " + answerKey);
        }
    }

    private LocalDateTime dateTime(String answerKey, JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.asString());
        } catch (DateTimeParseException e) {
            throw new InvalidAnswerException(
                    "'" + value.asString() + "' is not a valid ISO date-time for " + answerKey);
        }
    }

    private void mergeIntoAdditionalFacts(FamilyMember familyMember, String answerKey, JsonNode value) {
        JsonNode existing = jsonColumnMapper.read(familyMember.getAdditionalFacts());
        ObjectNode merged = existing instanceof ObjectNode existingObject
                ? existingObject.deepCopy()
                : objectMapper.createObjectNode();
        merged.set(answerKey, value);
        familyMember.setAdditionalFacts(jsonColumnMapper.write(merged));
    }
}
