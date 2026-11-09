package com.questionnaire.questionnaire_platform.exception;

// PedigreeDraw rejected the exported document as invalid against its own schema (HTTP 422) -
// should be rare, since PedigreeExportService already produces a schema-complete document even
// mid-assessment, but surfaced distinctly from an unreachable service or an unexpected failure.
public class InvalidPedigreeDocumentException extends RuntimeException {
    public InvalidPedigreeDocumentException(String message) {
        super(message);
    }
}
