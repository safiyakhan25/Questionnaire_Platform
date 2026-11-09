package com.questionnaire.questionnaire_platform.exception;

// A /pedigree-diagram request named a format PedigreeDraw doesn't support - rejected before ever
// calling PedigreeDraw, both to fail fast with a clear 400 and so an unrecognized or malicious
// format string (e.g. one smuggling extra query parameters) never reaches the upstream service.
public class InvalidPedigreeFormatException extends RuntimeException {
    public InvalidPedigreeFormatException(String message) {
        super(message);
    }
}
