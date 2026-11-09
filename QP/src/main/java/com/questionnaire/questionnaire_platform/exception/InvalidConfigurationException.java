package com.questionnaire.questionnaire_platform.exception;

// Step 7: thrown when admin-submitted configuration (a rule, a question's validation JSON, its
// options) is itself structurally invalid - distinct from InvalidAnswerException (a patient's
// submitted value) and InvalidRelationshipException (a relationship's fields).
public class InvalidConfigurationException extends RuntimeException {
    public InvalidConfigurationException(String message) {
        super(message);
    }
}
