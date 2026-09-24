package com.questionnaire.questionnaire_platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// code is deliberately excluded - the SRS scopes updates to name, description, and displayOrder only.
public record WorkflowUpdateRequest(
        @NotBlank(message = "name is required")
        String name,

        String description,

        @NotNull(message = "displayOrder is required")
        Integer displayOrder
) {
}
