package com.questionnaire.questionnaire_platform.service;

// The rendered image PedigreeDraw returned - raw bytes plus the Content-Type it reported, so the
// controller can pass both straight through without knowing which format was requested.
public record PedigreeDiagram(byte[] bytes, String contentType) {
}
