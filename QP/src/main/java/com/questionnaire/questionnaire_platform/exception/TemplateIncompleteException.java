package com.questionnaire.questionnaire_platform.exception;

// FR-6: thrown when next-template is called but the current template still has required,
// currently-visible questions with no answer on record.
public class TemplateIncompleteException extends RuntimeException {
    public TemplateIncompleteException(String message) {
        super(message);
    }
}
