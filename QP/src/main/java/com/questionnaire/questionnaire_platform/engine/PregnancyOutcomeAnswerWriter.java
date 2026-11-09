package com.questionnaire.questionnaire_platform.engine;

import com.questionnaire.questionnaire_platform.entity.FamilyMember;
import com.questionnaire.questionnaire_platform.entity.Person;
import com.questionnaire.questionnaire_platform.entity.PregnancyOutcome;
import com.questionnaire.questionnaire_platform.entity.PregnancyOutcomeType;
import com.questionnaire.questionnaire_platform.exception.InvalidAnswerException;
import com.questionnaire.questionnaire_platform.repository.PregnancyOutcomeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

// Maps to individual.pregnancyOutcome (pedigree_schema.json) - FAMILY_MEMBER_ONLY by construction
// (a pregnancy loss or current pregnancy is never the proband; see PregnancyOutcome.java), so
// applyToPerson is never expected to be reached in practice and throws defensively rather than
// silently no-op-ing. Value shape: null (not applicable), or {"outcomeType": "SAB",
// "gestationalWeeks": 10, "lmpDate": "2024-01-15"}.
@Component
@RequiredArgsConstructor
public class PregnancyOutcomeAnswerWriter {

    public static final String ANSWER_KEY = "pregnancy_outcome";

    private final PregnancyOutcomeRepository pregnancyOutcomeRepository;

    public void applyToPerson(Person person, JsonNode value) {
        throw new InvalidAnswerException("'" + ANSWER_KEY + "' cannot be recorded for the proband");
    }

    public void applyToFamilyMember(FamilyMember familyMember, JsonNode value) {
        pregnancyOutcomeRepository.deleteByFamilyMemberId(familyMember.getId());
        parseEntry(value).ifPresent(entry -> {
            PregnancyOutcome outcome = new PregnancyOutcome();
            outcome.setFamilyMember(familyMember);
            outcome.setOutcomeType(entry.outcomeType());
            outcome.setGestationalWeeks(entry.gestationalWeeks());
            outcome.setLmpDate(entry.lmpDate());
            outcome.setCreatedAt(LocalDateTime.now());
            pregnancyOutcomeRepository.save(outcome);
        });
    }

    private record Entry(PregnancyOutcomeType outcomeType, Integer gestationalWeeks, LocalDate lmpDate) {
    }

    private Optional<Entry> parseEntry(JsonNode value) {
        if (value == null || value.isNull()) {
            return Optional.empty();
        }
        if (value.getNodeType() != JsonNodeType.OBJECT) {
            throw new InvalidAnswerException("'" + ANSWER_KEY + "' must be an object or null");
        }
        if (!value.has("outcomeType") || !value.get("outcomeType").isString()) {
            throw new InvalidAnswerException("'" + ANSWER_KEY + "' requires outcomeType");
        }
        PregnancyOutcomeType outcomeType;
        try {
            outcomeType = PregnancyOutcomeType.valueOf(value.get("outcomeType").asString());
        } catch (IllegalArgumentException e) {
            throw new InvalidAnswerException("'" + ANSWER_KEY + "' has an invalid outcomeType: " + value.get("outcomeType").asString());
        }
        Integer gestationalWeeks = value.has("gestationalWeeks") && !value.get("gestationalWeeks").isNull()
                ? value.get("gestationalWeeks").asInt() : null;
        LocalDate lmpDate = value.has("lmpDate") && value.get("lmpDate").isString()
                ? LocalDate.parse(value.get("lmpDate").asString()) : null;
        return Optional.of(new Entry(outcomeType, gestationalWeeks, lmpDate));
    }
}
