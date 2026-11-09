package com.questionnaire.questionnaire_platform.engine;

import com.questionnaire.questionnaire_platform.entity.Question;
import com.questionnaire.questionnaire_platform.exception.InvalidAnswerException;
import com.questionnaire.questionnaire_platform.util.JsonColumnMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeType;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

// FR-15: validates a submitted answer against its question's configuration before it is persisted
// or used to update a business entity - required/data-type/allowed-values/range/format, using the
// fixed named-key vocabulary on question.validation (required, minLength/maxLength, min/max,
// pattern) plus question.options for RADIO/DROPDOWN/CHECKBOX. Throws InvalidAnswerException (400)
// on the first violation found; never mutates anything.
@Component
@RequiredArgsConstructor
public class AnswerValidator {

    private final JsonColumnMapper jsonColumnMapper;

    public void validate(Question question, JsonNode value) {
        JsonNode validation = jsonColumnMapper.read(question.getValidation());
        boolean required = validation != null && validation.path("required").asBoolean(false);

        if (isBlank(value)) {
            if (required) {
                throw new InvalidAnswerException("'" + question.getAnswerKey() + "' is required");
            }
            return;
        }

        switch (question.getType()) {
            case TEXT -> validateText(question, value, validation);
            case NUMBER -> validateNumber(question, value, validation);
            case DATE -> validateDate(question, value, validation);
            case RADIO, DROPDOWN -> validateSingleAllowedValue(question, value);
            case CHECKBOX -> validateMultipleAllowedValues(question, value);
            // Deep shape validation for a structured answer is the answer-writer's job
            // (DiseaseAnswerWriter etc. already throw InvalidAnswerException on a malformed
            // shape) - only the required-check above applies here.
            case STRUCTURED -> {
            }
        }
    }

    // Shared with AssessmentService's FR-6 completion check: "has this question actually been
    // answered" needs to agree with "was this value acceptable to submit" - one source of truth.
    public boolean isAnswered(JsonNode value) {
        return !isBlank(value);
    }

    private boolean isBlank(JsonNode value) {
        if (value == null || value.isNull()) {
            return true;
        }
        if (value.getNodeType() == JsonNodeType.STRING && value.asString().isBlank()) {
            return true;
        }
        return value.getNodeType() == JsonNodeType.ARRAY && value.isEmpty();
    }

    private void validateText(Question question, JsonNode value, JsonNode validation) {
        requireType(question, value, JsonNodeType.STRING, "text");
        String text = value.asString();
        if (validation == null) {
            return;
        }
        if (validation.has("minLength") && text.length() < validation.get("minLength").asInt()) {
            throw new InvalidAnswerException(
                    "'" + question.getAnswerKey() + "' must be at least " + validation.get("minLength").asInt() + " characters");
        }
        if (validation.has("maxLength") && text.length() > validation.get("maxLength").asInt()) {
            throw new InvalidAnswerException(
                    "'" + question.getAnswerKey() + "' must be at most " + validation.get("maxLength").asInt() + " characters");
        }
        if (validation.has("pattern") && !text.matches(validation.get("pattern").asString())) {
            throw new InvalidAnswerException("'" + question.getAnswerKey() + "' does not match the required format");
        }
    }

    private void validateNumber(Question question, JsonNode value, JsonNode validation) {
        requireType(question, value, JsonNodeType.NUMBER, "a number");
        double number = value.asDouble();
        if (validation == null) {
            return;
        }
        if (validation.has("min") && number < validation.get("min").asDouble()) {
            throw new InvalidAnswerException(
                    "'" + question.getAnswerKey() + "' must be at least " + validation.get("min").asDouble());
        }
        if (validation.has("max") && number > validation.get("max").asDouble()) {
            throw new InvalidAnswerException(
                    "'" + question.getAnswerKey() + "' must be at most " + validation.get("max").asDouble());
        }
    }

    private void validateDate(Question question, JsonNode value, JsonNode validation) {
        requireType(question, value, JsonNodeType.STRING, "an ISO date-time");
        LocalDateTime date = parseDate(question, value.asString());
        if (validation == null) {
            return;
        }
        if (validation.has("min")) {
            LocalDateTime min = parseDate(question, validation.get("min").asString());
            if (date.isBefore(min)) {
                throw new InvalidAnswerException("'" + question.getAnswerKey() + "' must be on or after " + min);
            }
        }
        if (validation.has("max")) {
            LocalDateTime max = parseDate(question, validation.get("max").asString());
            if (date.isAfter(max)) {
                throw new InvalidAnswerException("'" + question.getAnswerKey() + "' must be on or before " + max);
            }
        }
    }

    // Matches PersonAnswerWriter/FamilyMemberAnswerWriter's own LocalDateTime.parse exactly -
    // an answer that passes validation must also be writable to the business entity.
    private LocalDateTime parseDate(Question question, String text) {
        try {
            return LocalDateTime.parse(text);
        } catch (DateTimeParseException e) {
            throw new InvalidAnswerException(
                    "'" + question.getAnswerKey() + "' must be a valid ISO date-time, got '" + text + "'");
        }
    }

    private void validateSingleAllowedValue(Question question, JsonNode value) {
        JsonNode options = jsonColumnMapper.read(question.getOptions());
        if (options == null || options.getNodeType() != JsonNodeType.ARRAY) {
            return;
        }
        for (JsonNode option : options) {
            if (option.has("value") && option.get("value").equals(value)) {
                return;
            }
        }
        throw new InvalidAnswerException("'" + question.getAnswerKey() + "' must be one of the configured option values");
    }

    private void validateMultipleAllowedValues(Question question, JsonNode value) {
        requireType(question, value, JsonNodeType.ARRAY, "a list of selected values");
        JsonNode options = jsonColumnMapper.read(question.getOptions());
        if (options == null || options.getNodeType() != JsonNodeType.ARRAY) {
            return;
        }
        for (JsonNode selected : value) {
            boolean matched = false;
            for (JsonNode option : options) {
                if (option.has("value") && option.get("value").equals(selected)) {
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                throw new InvalidAnswerException("'" + question.getAnswerKey() + "' contains a value not in the configured options");
            }
        }
    }

    private void requireType(Question question, JsonNode value, JsonNodeType expected, String description) {
        if (value.getNodeType() != expected) {
            throw new InvalidAnswerException("'" + question.getAnswerKey() + "' must be " + description);
        }
    }
}
