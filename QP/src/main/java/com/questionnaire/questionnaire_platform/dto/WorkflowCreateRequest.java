package com.questionnaire.questionnaire_platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record WorkflowCreateRequest(
        @NotBlank(message = "code is required")
        String code,

        @NotBlank(message = "name is required")
        String name,

        String description,

        @NotNull(message = "displayOrder is required")
        Integer displayOrder
) {
}
