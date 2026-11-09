package com.questionnaire.questionnaire_platform.engine;

import com.questionnaire.questionnaire_platform.entity.GenderIdentityType;
import com.questionnaire.questionnaire_platform.entity.Person;
import com.questionnaire.questionnaire_platform.entity.SexAssignedAtBirthType;
import com.questionnaire.questionnaire_platform.util.JsonColumnMapper;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PersonAnswerWriterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JsonColumnMapper jsonColumnMapper = new JsonColumnMapper(objectMapper);
    private final PersonAnswerWriter writer = new PersonAnswerWriter(jsonColumnMapper, objectMapper);

    @Test
    void writesKnownAnswerKeysToFixedColumns() {
        Person person = new Person();
        writer.apply(person, "first_name", objectMapper.readTree("\"Safiya\""));
        writer.apply(person, "sex_assigned_at_birth", objectMapper.readTree("\"FEMALE\""));
        writer.apply(person, "gender_identity", objectMapper.readTree("\"WOMAN\""));
        writer.apply(person, "is_intersex", objectMapper.readTree("false"));
        writer.apply(person, "age", objectMapper.readTree("30"));
        writer.apply(person, "deceased", objectMapper.readTree("false"));

        assertThat(person.getFirstName()).isEqualTo("Safiya");
        assertThat(person.getSexAssignedAtBirth()).isEqualTo(SexAssignedAtBirthType.FEMALE);
        assertThat(person.getGenderIdentity()).isEqualTo(GenderIdentityType.WOMAN);
        assertThat(person.getIsIntersex()).isFalse();
        assertThat(person.getAge()).isEqualTo(30);
        assertThat(person.getDeceased()).isFalse();
    }

    @Test
    void writesDateTimeAnswersAsLocalDateTime() {
        Person person = new Person();
        writer.apply(person, "birth_date", objectMapper.readTree("\"2000-01-01T00:00:00\""));
        assertThat(person.getBirthDate()).isEqualTo(LocalDateTime.parse("2000-01-01T00:00:00"));
    }

    @Test
    void unknownAnswerKeyFallsIntoAdditionalFacts() {
        Person person = new Person();
        writer.apply(person, "custom_fact", objectMapper.readTree("\"custom value\""));
        assertThat(person.getAdditionalFacts()).contains("custom_fact").contains("custom value");
    }

    @Test
    void nullValueClearsAField() {
        Person person = new Person();
        person.setFirstName("Safiya");
        writer.apply(person, "first_name", objectMapper.readTree("null"));
        assertThat(person.getFirstName()).isNull();
    }

    @Test
    void writesStringArrayFields() {
        Person person = new Person();
        writer.apply(person, "ethnicity", objectMapper.readTree("[\"Asian\",\"South Asian\"]"));
        assertThat(person.getEthnicity()).containsExactly("Asian", "South Asian");
    }
}
