package com.questionnaire.questionnaire_platform.engine;

import com.questionnaire.questionnaire_platform.entity.Question;
import com.questionnaire.questionnaire_platform.entity.QuestionType;
import com.questionnaire.questionnaire_platform.exception.InvalidAnswerException;
import com.questionnaire.questionnaire_platform.util.JsonColumnMapper;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnswerValidatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JsonColumnMapper jsonColumnMapper = new JsonColumnMapper(objectMapper);
    private final AnswerValidator validator = new AnswerValidator(jsonColumnMapper);

    private Question question(QuestionType type, String validationJson, String optionsJson) {
        Question question = new Question();
        question.setAnswerKey("test_key");
        question.setType(type);
        question.setValidation(validationJson);
        question.setOptions(optionsJson);
        return question;
    }

    @Test
    void requiredQuestionRejectsBlankValue() {
        Question q = question(QuestionType.TEXT, "{\"required\":true}", null);
        assertThatThrownBy(() -> validator.validate(q, objectMapper.readTree("\"\"")))
                .isInstanceOf(InvalidAnswerException.class)
                .hasMessageContaining("required");
    }

    @Test
    void optionalQuestionAcceptsNullValue() {
        Question q = question(QuestionType.TEXT, "{\"required\":false}", null);
        assertThatCode(() -> validator.validate(q, objectMapper.readTree("null"))).doesNotThrowAnyException();
    }

    @Test
    void textQuestionRejectsNonStringValue() {
        Question q = question(QuestionType.TEXT, null, null);
        assertThatThrownBy(() -> validator.validate(q, objectMapper.readTree("123")))
                .isInstanceOf(InvalidAnswerException.class)
                .hasMessageContaining("text");
    }

    @Test
    void textQuestionEnforcesMaxLength() {
        Question q = question(QuestionType.TEXT, "{\"maxLength\":5}", null);
        assertThatThrownBy(() -> validator.validate(q, objectMapper.readTree("\"toolongvalue\"")))
                .isInstanceOf(InvalidAnswerException.class)
                .hasMessageContaining("at most 5");
    }

    @Test
    void numberQuestionEnforcesRange() {
        Question q = question(QuestionType.NUMBER, "{\"min\":0,\"max\":120}", null);
        assertThatThrownBy(() -> validator.validate(q, objectMapper.readTree("150")))
                .isInstanceOf(InvalidAnswerException.class)
                .hasMessageContaining("at most");
        assertThatCode(() -> validator.validate(q, objectMapper.readTree("45"))).doesNotThrowAnyException();
    }

    @Test
    void dateQuestionRejectsUnparsableValue() {
        Question q = question(QuestionType.DATE, null, null);
        assertThatThrownBy(() -> validator.validate(q, objectMapper.readTree("\"not-a-date\"")))
                .isInstanceOf(InvalidAnswerException.class);
        assertThatCode(() -> validator.validate(q, objectMapper.readTree("\"2000-01-01T00:00:00\"")))
                .doesNotThrowAnyException();
    }

    @Test
    void radioQuestionRejectsValueNotInOptions() {
        Question q = question(QuestionType.RADIO, null,
                "[{\"label\":\"Male\",\"value\":\"MALE\"},{\"label\":\"Female\",\"value\":\"FEMALE\"}]");
        assertThatThrownBy(() -> validator.validate(q, objectMapper.readTree("\"ALIEN\"")))
                .isInstanceOf(InvalidAnswerException.class);
        assertThatCode(() -> validator.validate(q, objectMapper.readTree("\"MALE\""))).doesNotThrowAnyException();
    }

    @Test
    void checkboxQuestionRequiresArrayAndValidatesEachElement() {
        Question q = question(QuestionType.CHECKBOX,
                null, "[{\"label\":\"A\",\"value\":\"A\"},{\"label\":\"B\",\"value\":\"B\"}]");
        assertThatThrownBy(() -> validator.validate(q, objectMapper.readTree("\"A\"")))
                .isInstanceOf(InvalidAnswerException.class);
        assertThatThrownBy(() -> validator.validate(q, objectMapper.readTree("[\"A\",\"C\"]")))
                .isInstanceOf(InvalidAnswerException.class);
        assertThatCode(() -> validator.validate(q, objectMapper.readTree("[\"A\",\"B\"]"))).doesNotThrowAnyException();
    }

    @Test
    void patternValidationEnforcesRegex() {
        Question q = question(QuestionType.TEXT, "{\"pattern\":\"^[^@]+@[^@]+$\"}", null);
        assertThatThrownBy(() -> validator.validate(q, objectMapper.readTree("\"not-an-email\"")))
                .isInstanceOf(InvalidAnswerException.class);
        assertThatCode(() -> validator.validate(q, objectMapper.readTree("\"a@b.com\""))).doesNotThrowAnyException();
    }

    @Test
    void structuredAnswerPassesWithoutTypeSpecificValidation() {
        Question q = question(QuestionType.STRUCTURED, null, null);
        assertThatCode(() -> validator.validate(q,
                objectMapper.readTree("{\"twinType\":\"MONOZYGOTIC\",\"coTwinFamilyMemberIds\":[\"11111111-1111-1111-1111-111111111111\"]}")))
                .doesNotThrowAnyException();
    }

    @Test
    void requiredStructuredAnswerStillRejectsBlank() {
        Question q = question(QuestionType.STRUCTURED, "{\"required\":true}", null);
        assertThatThrownBy(() -> validator.validate(q, objectMapper.readTree("null")))
                .isInstanceOf(InvalidAnswerException.class);
    }

    @Test
    void isAnsweredDistinguishesBlankFromPresent() {
        assertThat(validator.isAnswered(objectMapper.readTree("null"))).isFalse();
        assertThat(validator.isAnswered(objectMapper.readTree("\"\""))).isFalse();
        assertThat(validator.isAnswered(objectMapper.readTree("[]"))).isFalse();
        assertThat(validator.isAnswered(objectMapper.readTree("\"x\""))).isTrue();
    }
}
