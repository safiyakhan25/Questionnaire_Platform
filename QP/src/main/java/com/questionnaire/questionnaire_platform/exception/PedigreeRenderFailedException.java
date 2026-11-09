package com.questionnaire.questionnaire_platform.exception;

// PedigreeDraw responded with something other than 200 (success) or 422 (invalid document) - an
// unexpected failure on its side, surfaced as a bad-gateway rather than guessed at further.
public class PedigreeRenderFailedException extends RuntimeException {
    public PedigreeRenderFailedException(String message) {
        super(message);
    }
}
