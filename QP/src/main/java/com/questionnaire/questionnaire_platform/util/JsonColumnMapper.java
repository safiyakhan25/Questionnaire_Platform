package com.questionnaire.questionnaire_platform.util;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

// Converts between the JsonNode shape used in request/response DTOs and the raw JSON text
// stored in JSONB columns (validation, options, templateRule, visibilityRule, disableRule).
@Component
@RequiredArgsConstructor
public class JsonColumnMapper {

    private final ObjectMapper objectMapper;

    public String write(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return node.toString();
    }

    public JsonNode read(String json) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (JacksonException e) {
            throw new IllegalStateException("Stored JSON could not be parsed: " + e.getMessage(), e);
        }
    }
}
