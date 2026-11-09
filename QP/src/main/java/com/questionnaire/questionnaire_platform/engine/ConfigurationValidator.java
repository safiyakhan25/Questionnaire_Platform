package com.questionnaire.questionnaire_platform.engine;

import com.questionnaire.questionnaire_platform.entity.QuestionType;
import com.questionnaire.questionnaire_platform.exception.InvalidConfigurationException;
import com.questionnaire.questionnaire_platform.util.JsonColumnMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeType;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

// Step 7: validates admin-submitted configuration at save time - a workflow/template/question
// create or update - rather than letting a structurally broken rule or validation config get
// stored and only fail later, mid-assessment, as an opaque 500 (RuleEvaluationException) or a
// silently-ignored typo (e.g. "required": "yes" being misread as false forever).
@Component
@RequiredArgsConstructor
public class ConfigurationValidator {

    private static final Set<String> KNOWN_VALIDATION_KEYS =
            Set.of("required", "minLength", "maxLength", "min", "max", "pattern");

    private final RuleEngine ruleEngine;
    private final JsonColumnMapper jsonColumnMapper;

    // Null means "no rule configured", always valid (FR-2/FR-5's "unconditionally shown/loaded").
    // Evaluated against an empty facts map purely to exercise JsonLogic's own parsing/operator
    // validation - {"var": X} references just resolve to null against {}, which is not itself an
    // error, so this is a structural check, not a "does this rule currently evaluate true" check.
    public void validateRule(JsonNode rule, String fieldName) {
        if (rule == null || rule.isNull()) {
            return;
        }
        try {
            ruleEngine.evaluate(jsonColumnMapper.write(rule), Map.of());
        } catch (RuleEvaluationException e) {
            throw new InvalidConfigurationException(
                    "'" + fieldName + "' is not a valid JsonLogic rule: " + e.getCause().getMessage());
        }
    }

    // FR-15's closed named-key vocabulary, validated at config time instead of only being
    // discovered the first time AnswerValidator reads a misconfigured value at runtime.
    public void validateQuestionValidation(JsonNode validation) {
        if (validation == null || validation.isNull()) {
            return;
        }
        if (validation.getNodeType() != JsonNodeType.OBJECT) {
            throw new InvalidConfigurationException("'validation' must be a JSON object");
        }

        validation.propertyNames().forEach(key -> {
            if (!KNOWN_VALIDATION_KEYS.contains(key)) {
                throw new InvalidConfigurationException(
                        "'validation' contains unknown key '" + key + "' - must be one of " + KNOWN_VALIDATION_KEYS);
            }
        });

        if (validation.has("required") && validation.get("required").getNodeType() != JsonNodeType.BOOLEAN) {
            throw new InvalidConfigurationException("'validation.required' must be a boolean");
        }

        Integer minLength = requireIntIfPresent(validation, "minLength");
        Integer maxLength = requireIntIfPresent(validation, "maxLength");
        if (minLength != null && maxLength != null && minLength > maxLength) {
            throw new InvalidConfigurationException(
                    "'validation.minLength' (" + minLength + ") cannot be greater than 'maxLength' (" + maxLength + ")");
        }

        Double min = requireNumberIfPresent(validation, "min");
        Double max = requireNumberIfPresent(validation, "max");
        if (min != null && max != null && min > max) {
            throw new InvalidConfigurationException(
                    "'validation.min' (" + min + ") cannot be greater than 'max' (" + max + ")");
        }

        if (validation.has("pattern")) {
            if (validation.get("pattern").getNodeType() != JsonNodeType.STRING) {
                throw new InvalidConfigurationException("'validation.pattern' must be a string");
            }
            try {
                Pattern.compile(validation.get("pattern").asString());
            } catch (PatternSyntaxException e) {
                throw new InvalidConfigurationException("'validation.pattern' is not a valid regular expression: " + e.getMessage());
            }
        }
    }

    // Only meaningful for RADIO/DROPDOWN/CHECKBOX, but validates whatever shape is given
    // regardless of type - not an error to supply options for a type that won't read them.
    public void validateOptions(JsonNode options) {
        if (options == null || options.isNull()) {
            return;
        }
        if (options.getNodeType() != JsonNodeType.ARRAY) {
            throw new InvalidConfigurationException("'options' must be a JSON array");
        }
        for (JsonNode option : options) {
            boolean hasLabel = option.getNodeType() == JsonNodeType.OBJECT
                    && option.has("label") && option.get("label").getNodeType() == JsonNodeType.STRING
                    && !option.get("label").asString().isBlank();
            boolean hasValue = option.getNodeType() == JsonNodeType.OBJECT && option.has("value");
            if (!hasLabel || !hasValue) {
                throw new InvalidConfigurationException(
                        "Each entry in 'options' requires a non-blank 'label' and a 'value'");
            }
        }
    }

    private static final Set<String> KNOWN_FIELD_TYPES = Set.of(
            "TEXT", "NUMBER", "DATE", "DROPDOWN", "FAMILY_MEMBER_SELECT", "FAMILY_MEMBER_MULTI_SELECT", "GROUP_LIST");

    public void validateForType(QuestionType type, JsonNode validation, JsonNode options, JsonNode fieldConfig) {
        validateQuestionValidation(validation);
        validateOptions(options);
        validateFieldConfig(fieldConfig);
        if (type == QuestionType.STRUCTURED) {
            if (fieldConfig == null || fieldConfig.isNull()) {
                throw new InvalidConfigurationException("'fieldConfig' is required for a STRUCTURED question");
            }
            if (validation != null && validation.path("required").asBoolean(false)) {
                throw new InvalidConfigurationException(
                        "'validation.required' cannot be true for a STRUCTURED question - see "
                                + "docs/superpowers/specs/2026-11-01-structured-pedigree-questions-design.md §4.2");
            }
        }
    }

    // Only meaningful for STRUCTURED, but validates whatever shape is given regardless of type -
    // see docs/superpowers/specs/2026-11-01-structured-pedigree-questions-design.md §4.1/§4.2.
    public void validateFieldConfig(JsonNode fieldConfig) {
        if (fieldConfig == null || fieldConfig.isNull()) {
            return;
        }
        if (fieldConfig.getNodeType() != JsonNodeType.OBJECT) {
            throw new InvalidConfigurationException("'fieldConfig' must be a JSON object");
        }
        String shape = fieldConfig.path("shape").asString(null);
        if (!"object".equals(shape) && !"list".equals(shape)) {
            throw new InvalidConfigurationException("'fieldConfig.shape' must be 'object' or 'list'");
        }
        JsonNode fields = fieldConfig.path("fields");
        if (fields.getNodeType() != JsonNodeType.ARRAY || fields.isEmpty()) {
            throw new InvalidConfigurationException("'fieldConfig.fields' must be a non-empty array");
        }
        for (JsonNode field : fields) {
            validateFieldDef(field);
        }
    }

    private void validateFieldDef(JsonNode field) {
        if (field.getNodeType() != JsonNodeType.OBJECT
                || field.path("name").asString("").isBlank()
                || field.path("label").asString("").isBlank()) {
            throw new InvalidConfigurationException("Each entry in 'fieldConfig.fields' requires a non-blank 'name' and 'label'");
        }
        String type = field.path("type").asString(null);
        if (type == null || !KNOWN_FIELD_TYPES.contains(type)) {
            throw new InvalidConfigurationException(
                    "'fieldConfig.fields[].type' must be one of " + KNOWN_FIELD_TYPES + ", got '" + type + "'");
        }
        if ("DROPDOWN".equals(type)) {
            JsonNode options = field.path("options");
            if (options.getNodeType() != JsonNodeType.ARRAY || options.isEmpty()) {
                throw new InvalidConfigurationException("A 'DROPDOWN' field in 'fieldConfig.fields' requires a non-empty 'options' array");
            }
            validateOptions(options);
        }
        if ("GROUP_LIST".equals(type)) {
            JsonNode nestedFields = field.path("fields");
            if (nestedFields.getNodeType() != JsonNodeType.ARRAY || nestedFields.isEmpty()) {
                throw new InvalidConfigurationException("A 'GROUP_LIST' field in 'fieldConfig.fields' requires its own non-empty 'fields' array");
            }
            for (JsonNode nestedField : nestedFields) {
                validateFieldDef(nestedField);
            }
        }
    }

    private Integer requireIntIfPresent(JsonNode validation, String key) {
        if (!validation.has(key)) {
            return null;
        }
        JsonNode value = validation.get(key);
        if (value.getNodeType() != JsonNodeType.NUMBER) {
            throw new InvalidConfigurationException("'validation." + key + "' must be a number");
        }
        return value.asInt();
    }

    private Double requireNumberIfPresent(JsonNode validation, String key) {
        if (!validation.has(key)) {
            return null;
        }
        JsonNode value = validation.get(key);
        if (value.getNodeType() != JsonNodeType.NUMBER) {
            throw new InvalidConfigurationException("'validation." + key + "' must be a number");
        }
        return value.asDouble();
    }
}
