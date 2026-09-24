package com.questionnaire.questionnaire_platform.dto;

import com.questionnaire.questionnaire_platform.entity.Workflow;

import java.time.LocalDateTime;
import java.util.UUID;

public record WorkflowResponse(
        UUID id,
        String code,
        String name,
        String description,
        Integer displayOrder,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static WorkflowResponse from(Workflow workflow) {
        return new WorkflowResponse(
                workflow.getId(),
                workflow.getCode(),
                workflow.getName(),
                workflow.getDescription(),
                workflow.getDisplayOrder(),
                workflow.getCreatedAt(),
                workflow.getUpdatedAt()
        );
    }
}
