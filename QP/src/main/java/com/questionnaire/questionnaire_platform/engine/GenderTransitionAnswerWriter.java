package com.questionnaire.questionnaire_platform.engine;

import com.questionnaire.questionnaire_platform.entity.FamilyMember;
import com.questionnaire.questionnaire_platform.entity.GenderIdentityType;
import com.questionnaire.questionnaire_platform.entity.GenderTransition;
import com.questionnaire.questionnaire_platform.entity.Person;
import com.questionnaire.questionnaire_platform.exception.InvalidAnswerException;
import com.questionnaire.questionnaire_platform.repository.GenderTransitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeType;

import java.time.LocalDateTime;

// At most one GENDER_TRANSITION row per individual (pedigree_schema.json's genderTransition is a
// single optional object, not an array) - full replace on every submit, same reconciliation idiom
// as DiseaseAnswerWriter/MutationAnswerWriter. Value shape: null (no transition), or
// {"fromGender": "MAN", "toGender": "WOMAN", "transitionYear": 2020}.
@Component
@RequiredArgsConstructor
public class GenderTransitionAnswerWriter {

    public static final String ANSWER_KEY = "gender_transition";

    private final GenderTransitionRepository genderTransitionRepository;

    public void applyToPerson(Person person, JsonNode value) {
        genderTransitionRepository.deleteByPersonId(person.getId());
        parseEntry(value).ifPresent(entry -> {
            GenderTransition transition = new GenderTransition();
            transition.setPerson(person);
            populate(transition, entry);
            genderTransitionRepository.save(transition);
        });
    }

    public void applyToFamilyMember(FamilyMember familyMember, JsonNode value) {
        genderTransitionRepository.deleteByFamilyMemberId(familyMember.getId());
        parseEntry(value).ifPresent(entry -> {
            GenderTransition transition = new GenderTransition();
            transition.setFamilyMember(familyMember);
            populate(transition, entry);
            genderTransitionRepository.save(transition);
        });
    }

    private void populate(GenderTransition transition, Entry entry) {
        transition.setFromGender(entry.fromGender());
        transition.setToGender(entry.toGender());
        transition.setTransitionYear(entry.transitionYear());
        transition.setCreatedAt(LocalDateTime.now());
    }

    private record Entry(GenderIdentityType fromGender, GenderIdentityType toGender, Integer transitionYear) {
    }

    private java.util.Optional<Entry> parseEntry(JsonNode value) {
        if (value == null || value.isNull()) {
            return java.util.Optional.empty();
        }
        if (value.getNodeType() != JsonNodeType.OBJECT) {
            throw new InvalidAnswerException("'" + ANSWER_KEY + "' must be an object or null");
        }
        boolean hasFromGender = value.has("fromGender") && value.get("fromGender").isString();
        boolean hasToGender = value.has("toGender") && value.get("toGender").isString();
        if (!hasFromGender || !hasToGender) {
            throw new InvalidAnswerException("'" + ANSWER_KEY + "' requires fromGender and toGender");
        }
        GenderIdentityType fromGender = parseGender(value.get("fromGender").asString());
        GenderIdentityType toGender = parseGender(value.get("toGender").asString());
        Integer transitionYear = value.has("transitionYear") && !value.get("transitionYear").isNull()
                ? value.get("transitionYear").asInt() : null;
        return java.util.Optional.of(new Entry(fromGender, toGender, transitionYear));
    }

    private GenderIdentityType parseGender(String raw) {
        try {
            return GenderIdentityType.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw new InvalidAnswerException("'" + ANSWER_KEY + "' has an invalid gender value: " + raw);
        }
    }
}
