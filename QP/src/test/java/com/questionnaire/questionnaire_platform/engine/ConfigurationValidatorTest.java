package com.questionnaire.questionnaire_platform.engine;

import com.questionnaire.questionnaire_platform.entity.QuestionType;
import com.questionnaire.questionnaire_platform.exception.InvalidConfigurationException;
import com.questionnaire.questionnaire_platform.util.JsonColumnMapper;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigurationValidatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JsonColumnMapper jsonColumnMapper = new JsonColumnMapper(objectMapper);
    private final ConfigurationValidator validator = new ConfigurationValidator(new RuleEngine(), jsonColumnMapper);

    @Test
    void nullRuleIsValid() {
        assertThatCode(() -> validator.validateRule(null, "templateRule")).doesNotThrowAnyException();
    }

    @Test
    void wellFormedRuleIsValid() {
        assertThatCode(() -> validator.validateRule(
                objectMapper.readTree("{\"==\":[{\"var\":\"gender_at_birth\"},\"FEMALE\"]}"), "templateRule"))
                .doesNotThrowAnyException();
    }

    @Test
    void malformedRuleIsRejected() {
        assertThatThrownBy(() -> validator.validateRule(
                objectMapper.readTree("{\"notAnOperator\":[1,2]}"), "templateRule"))
                .isInstanceOf(InvalidConfigurationException.class)
                .hasMessageContaining("templateRule");
    }

    @Test
    void unknownValidationKeyIsRejected() {
        assertThatThrownBy(() -> validator.validateQuestionValidation(objectMapper.readTree("{\"minLenght\":5}")))
                .isInstanceOf(InvalidConfigurationException.class)
                .hasMessageContaining("minLenght");
    }

    @Test
    void requiredMustBeBoolean() {
        assertThatThrownBy(() -> validator.validateQuestionValidation(objectMapper.readTree("{\"required\":\"yes\"}")))
                .isInstanceOf(InvalidConfigurationException.class);
    }

    @Test
    void minLengthGreaterThanMaxLengthIsRejected() {
        assertThatThrownBy(() -> validator.validateQuestionValidation(
                objectMapper.readTree("{\"minLength\":10,\"maxLength\":5}")))
                .isInstanceOf(InvalidConfigurationException.class);
    }

    @Test
    void minGreaterThanMaxIsRejected() {
        assertThatThrownBy(() -> validator.validateQuestionValidation(objectMapper.readTree("{\"min\":10,\"max\":5}")))
                .isInstanceOf(InvalidConfigurationException.class);
    }

    @Test
    void invalidRegexPatternIsRejected() {
        assertThatThrownBy(() -> validator.validateQuestionValidation(objectMapper.readTree("{\"pattern\":\"[unclosed\"}")))
                .isInstanceOf(InvalidConfigurationException.class);
    }

    @Test
    void wellFormedValidationIsAccepted() {
        assertThatCode(() -> validator.validateQuestionValidation(
                objectMapper.readTree("{\"required\":true,\"minLength\":2,\"maxLength\":100,\"pattern\":\"^[A-Za-z]+$\"}")))
                .doesNotThrowAnyException();
    }

    @Test
    void optionMissingLabelIsRejected() {
        assertThatThrownBy(() -> validator.validateOptions(objectMapper.readTree("[{\"value\":\"X\"}]")))
                .isInstanceOf(InvalidConfigurationException.class);
    }

    @Test
    void wellFormedOptionsAreAccepted() {
        assertThatCode(() -> validator.validateOptions(
                objectMapper.readTree("[{\"label\":\"Yes\",\"value\":true},{\"label\":\"No\",\"value\":false}]")))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsAWellFormedListShapeFieldConfig() {
        var fieldConfig = objectMapper.readTree(
                "{\"shape\":\"list\",\"fields\":[{\"name\":\"gene\",\"label\":\"Gene\",\"type\":\"TEXT\",\"required\":true}]}");
        assertThatCode(() -> validator.validateForType(QuestionType.STRUCTURED, null, null, fieldConfig))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsFieldConfigWithAnUnknownFieldType() {
        var fieldConfig = objectMapper.readTree(
                "{\"shape\":\"object\",\"fields\":[{\"name\":\"x\",\"label\":\"X\",\"type\":\"BOGUS\",\"required\":true}]}");
        assertThatThrownBy(() -> validator.validateForType(QuestionType.STRUCTURED, null, null, fieldConfig))
                .isInstanceOf(InvalidConfigurationException.class);
    }

    @Test
    void rejectsAStructuredQuestionConfiguredAsRequired() {
        var validation = objectMapper.readTree("{\"required\": true}");
        var fieldConfig = objectMapper.readTree(
                "{\"shape\":\"object\",\"fields\":[{\"name\":\"x\",\"label\":\"X\",\"type\":\"TEXT\",\"required\":true}]}");
        assertThatThrownBy(() -> validator.validateForType(QuestionType.STRUCTURED, validation, null, fieldConfig))
                .isInstanceOf(InvalidConfigurationException.class)
                .hasMessageContaining("STRUCTURED");
    }

    @Test
    void aGroupListFieldRequiresItsOwnNestedFieldsArray() {
        var fieldConfig = objectMapper.readTree(
                "{\"shape\":\"object\",\"fields\":[{\"name\":\"participants\",\"label\":\"Participants\",\"type\":\"GROUP_LIST\",\"required\":false}]}");
        assertThatThrownBy(() -> validator.validateForType(QuestionType.STRUCTURED, null, null, fieldConfig))
                .isInstanceOf(InvalidConfigurationException.class);
    }

    @Test
    void nonStructuredQuestionsIgnoreFieldConfigValidationWhenNull() {
        assertThatCode(() -> validator.validateForType(QuestionType.TEXT, null, null, null))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsAStructuredQuestionWithNoFieldConfigAtAll() {
        assertThatThrownBy(() -> validator.validateForType(QuestionType.STRUCTURED, null, null, null))
                .isInstanceOf(InvalidConfigurationException.class)
                .hasMessageContaining("fieldConfig");
    }
}
