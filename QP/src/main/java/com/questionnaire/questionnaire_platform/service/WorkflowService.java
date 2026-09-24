package com.questionnaire.questionnaire_platform.service;

import com.questionnaire.questionnaire_platform.dto.WorkflowCreateRequest;
import com.questionnaire.questionnaire_platform.dto.WorkflowResponse;
import com.questionnaire.questionnaire_platform.dto.WorkflowUpdateRequest;
import com.questionnaire.questionnaire_platform.entity.Workflow;
import com.questionnaire.questionnaire_platform.exception.DuplicateResourceException;
import com.questionnaire.questionnaire_platform.exception.ResourceInUseException;
import com.questionnaire.questionnaire_platform.exception.ResourceNotFoundException;
import com.questionnaire.questionnaire_platform.repository.WorkflowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkflowService {

    private final WorkflowRepository workflowRepository;

    @Transactional
    public WorkflowResponse create(WorkflowCreateRequest request) {
        if (workflowRepository.existsByCode(request.code())) {
            throw new DuplicateResourceException("A workflow with code '" + request.code() + "' already exists");
        }
        if (workflowRepository.existsByDisplayOrder(request.displayOrder())) {
            throw new DuplicateResourceException(
                    "A workflow with displayOrder " + request.displayOrder() + " already exists");
        }

        Workflow workflow = new Workflow();
        workflow.setCode(request.code());
        workflow.setName(request.name());
        workflow.setDescription(request.description());
        workflow.setDisplayOrder(request.displayOrder());
        LocalDateTime now = LocalDateTime.now();
        workflow.setCreatedAt(now);
        workflow.setUpdatedAt(now);

        return WorkflowResponse.from(workflowRepository.save(workflow));
    }

    public List<WorkflowResponse> getAll() {
        return workflowRepository.findAllByOrderByDisplayOrderAsc().stream()
                .map(WorkflowResponse::from)
                .toList();
    }

    public WorkflowResponse getById(UUID id) {
        return WorkflowResponse.from(findWorkflowOrThrow(id));
    }

    @Transactional
    public WorkflowResponse update(UUID id, WorkflowUpdateRequest request) {
        Workflow workflow = findWorkflowOrThrow(id);
        if (workflowRepository.existsByDisplayOrderAndIdNot(request.displayOrder(), id)) {
            throw new DuplicateResourceException(
                    "A workflow with displayOrder " + request.displayOrder() + " already exists");
        }
        workflow.setName(request.name());
        workflow.setDescription(request.description());
        workflow.setDisplayOrder(request.displayOrder());
        workflow.setUpdatedAt(LocalDateTime.now());

        return WorkflowResponse.from(workflow);
    }

    @Transactional
    public void delete(UUID id) {
        Workflow workflow = findWorkflowOrThrow(id);
        if (workflowRepository.hasTemplates(id)) {
            throw new ResourceInUseException(
                    "Workflow '" + workflow.getCode() + "' still has templates and cannot be deleted");
        }
        workflowRepository.delete(workflow);
    }

    private Workflow findWorkflowOrThrow(UUID id) {
        return workflowRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Workflow " + id + " not found"));
    }
}
