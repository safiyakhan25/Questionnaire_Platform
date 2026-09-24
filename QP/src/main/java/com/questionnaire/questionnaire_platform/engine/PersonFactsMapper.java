package com.questionnaire.questionnaire_platform.engine;

import com.questionnaire.questionnaire_platform.entity.Person;
import com.questionnaire.questionnaire_platform.util.JsonColumnMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Builds the "facts" map the Rule Engine evaluates rules against: one entry per PERSON
// column, keyed by the same name used as a question's answer_key, plus whatever has been
// collected into additional_facts for anything without a fixed column.
@Component
@RequiredArgsConstructor
public class PersonFactsMapper {

    private final JsonColumnMapper jsonColumnMapper;
    private final ObjectMapper objectMapper;

    public Map<String, Object> toFacts(Person person) {
        Map<String, Object> facts = new HashMap<>();
        facts.put("first_name", person.getFirstName());
        facts.put("middle_name", person.getMiddleName());
        facts.put("last_name", person.getLastName());
        facts.put("preferred_name", person.getPreferredName());
        facts.put("gender_at_birth", person.getGenderAtBirth() == null ? null : person.getGenderAtBirth().name());
        facts.put("gender_identity", person.getGenderIdentity());
        facts.put("birth_date", person.getBirthDate() == null ? null : person.getBirthDate().toString());
        facts.put("marital_status", person.getMaritalStatus());
        facts.put("communication_language", person.getCommunicationLanguage());
        facts.put("race", person.getRace() == null ? null : List.of(person.getRace()));
        facts.put("ethnicity", person.getEthnicity() == null ? null : List.of(person.getEthnicity()));
        facts.put("blood_type", person.getBloodType());
        facts.put("deceased", person.getDeceased());
        facts.put("deceased_date_time", person.getDeceasedDateTime() == null ? null : person.getDeceasedDateTime().toString());
        facts.put("age", person.getAge());
        facts.put("primary_contact", person.getPrimaryContact());
        facts.put("contact", toMap(jsonColumnMapper.read(person.getContact())));
        facts.put("address", toMap(jsonColumnMapper.read(person.getAddress())));

        Map<String, Object> additionalFacts = toMap(jsonColumnMapper.read(person.getAdditionalFacts()));
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
