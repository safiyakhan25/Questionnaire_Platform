package com.questionnaire.questionnaire_platform.engine;

import com.questionnaire.questionnaire_platform.entity.FamilyMember;
import com.questionnaire.questionnaire_platform.entity.GenderIdentityType;
import com.questionnaire.questionnaire_platform.entity.SexAssignedAtBirthType;
import com.questionnaire.questionnaire_platform.util.JsonColumnMapper;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class FamilyMemberAnswerWriterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JsonColumnMapper jsonColumnMapper = new JsonColumnMapper(objectMapper);
    private final FamilyMemberAnswerWriter writer = new FamilyMemberAnswerWriter(jsonColumnMapper, objectMapper);

    @Test
    void writesKnownAnswerKeysToFixedColumns() {
        FamilyMember familyMember = new FamilyMember();
        writer.apply(familyMember, "first_name", objectMapper.readTree("\"Jane\""));
        writer.apply(familyMember, "sex_assigned_at_birth", objectMapper.readTree("\"FEMALE\""));
        writer.apply(familyMember, "gender_identity", objectMapper.readTree("\"WOMAN\""));
        writer.apply(familyMember, "age", objectMapper.readTree("50"));

        assertThat(familyMember.getFirstName()).isEqualTo("Jane");
        assertThat(familyMember.getSexAssignedAtBirth()).isEqualTo(SexAssignedAtBirthType.FEMALE);
        assertThat(familyMember.getGenderIdentity()).isEqualTo(GenderIdentityType.WOMAN);
        assertThat(familyMember.getAge()).isEqualTo(50);
    }

    @Test
    void writesDateTimeAnswersAsLocalDateTime() {
        FamilyMember familyMember = new FamilyMember();
        writer.apply(familyMember, "birth_date", objectMapper.readTree("\"1970-01-01T00:00:00\""));
        assertThat(familyMember.getBirthDate()).isEqualTo(LocalDateTime.parse("1970-01-01T00:00:00"));
    }

    @Test
    void unknownAnswerKeyFallsIntoAdditionalFacts() {
        FamilyMember familyMember = new FamilyMember();
        writer.apply(familyMember, "custom_fact", objectMapper.readTree("\"x\""));
        assertThat(familyMember.getAdditionalFacts()).contains("custom_fact");
    }

    @Test
    void relationshipTypeIsNotWritableThroughAnswers() {
        // relationship_type/maternal_paternal_side are set at creation, never through an answer -
        // submitting them as an answer_key must fall into additional_facts, not throw or be ignored silently.
        FamilyMember familyMember = new FamilyMember();
        writer.apply(familyMember, "relationshipType", objectMapper.readTree("\"MOTHER\""));
        assertThat(familyMember.getAdditionalFacts()).contains("relationshipType");
    }
}
