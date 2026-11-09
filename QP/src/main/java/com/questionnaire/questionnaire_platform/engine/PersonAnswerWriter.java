package com.questionnaire.questionnaire_platform.engine;

import com.questionnaire.questionnaire_platform.entity.GenderIdentityType;
import com.questionnaire.questionnaire_platform.entity.Person;
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

// Applies one submitted answer onto the PERSON business entity (FR-3.2). Every PERSON column
// that a question can answer is mapped explicitly by answer_key; anything not recognized here
// falls into additional_facts, the flexible JSONB bucket for facts without a fixed column.
@Component
@RequiredArgsConstructor
public class PersonAnswerWriter {

    private final JsonColumnMapper jsonColumnMapper;
    private final ObjectMapper objectMapper;

    public void apply(Person person, String answerKey, JsonNode value) {
        switch (answerKey) {
            case "first_name" -> person.setFirstName(text(value));
            case "middle_name" -> person.setMiddleName(text(value));
            case "last_name" -> person.setLastName(text(value));
            case "preferred_name" -> person.setPreferredName(text(value));
            case "sex_assigned_at_birth" -> person.setSexAssignedAtBirth(enumValue(answerKey, value, SexAssignedAtBirthType.class));
            case "gender_identity" -> person.setGenderIdentity(enumValue(answerKey, value, GenderIdentityType.class));
            case "is_intersex" -> person.setIsIntersex(value == null || value.isNull() ? null : value.asBoolean());
            case "country_of_origin" -> person.setCountryOfOrigin(stringArray(value));
            case "birth_date" -> person.setBirthDate(dateTime(answerKey, value));
            case "marital_status" -> person.setMaritalStatus(text(value));
            case "communication_language" -> person.setCommunicationLanguage(text(value));
            case "race" -> person.setRace(stringArray(value));
            case "ethnicity" -> person.setEthnicity(stringArray(value));
            case "blood_type" -> person.setBloodType(text(value));
            case "deceased" -> person.setDeceased(value == null || value.isNull() ? null : value.asBoolean());
            case "deceased_date_time" -> person.setDeceasedDateTime(dateTime(answerKey, value));
            case "age" -> person.setAge(value == null || value.isNull() ? null : value.asInt());
            case "primary_contact" -> person.setPrimaryContact(text(value));
            case "contact" -> person.setContact(jsonColumnMapper.write(value));
            case "address" -> person.setAddress(jsonColumnMapper.write(value));
            default -> mergeIntoAdditionalFacts(person, answerKey, value);
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

    private void mergeIntoAdditionalFacts(Person person, String answerKey, JsonNode value) {
        JsonNode existing = jsonColumnMapper.read(person.getAdditionalFacts());
        ObjectNode merged = existing instanceof ObjectNode existingObject
                ? existingObject.deepCopy()
                : objectMapper.createObjectNode();
        merged.set(answerKey, value);
        person.setAdditionalFacts(jsonColumnMapper.write(merged));
    }
}
