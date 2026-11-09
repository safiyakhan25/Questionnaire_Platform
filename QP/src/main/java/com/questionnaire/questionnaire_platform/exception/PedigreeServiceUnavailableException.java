package com.questionnaire.questionnaire_platform.exception;

// The PedigreeDraw rendering service could not be reached at all (connection refused, timeout) -
// an operational fact about that separate process, not a problem with the pedigree data itself.
public class PedigreeServiceUnavailableException extends RuntimeException {
    public PedigreeServiceUnavailableException(String message) {
        super(message);
    }
}
