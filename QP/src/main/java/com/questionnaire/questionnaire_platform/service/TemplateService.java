package com.questionnaire.questionnaire_platform.service;

import com.questionnaire.questionnaire_platform.dto.TemplateCreateRequest;
import com.questionnaire.questionnaire_platform.dto.TemplateResponse;
import com.questionnaire.questionnaire_platform.dto.TemplateUpdateRequest;
import com.questionnaire.questionnaire_platform.entity.Template;
import com.questionnaire.questionnaire_platform.entity.Workflow;
import com.questionnaire.questionnaire_platform.exception.DuplicateResourceException;
import com.questionnaire.questionnaire_platform.exception.ResourceInUseException;
import com.questionnaire.questionnaire_platform.exception.ResourceNotFoundException;
import com.questionnaire.questionnaire_platform.repository.TemplateRepository;
import com.questionnaire.questionnaire_platform.repository.WorkflowRepository;
import com.questionnaire.questionnaire_platform.util.JsonColumnMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TemplateService {

    private final TemplateRepository templateRepository;
    private final WorkflowRepository workflowRepository;
    private final JsonColumnMapper jsonColumnMapper;

    @Transactional
    public TemplateResponse create(UUID workflowId, TemplateCreateRequest request) {
        Workflow workflow = findWorkflowOrThrow(workflowId);

        if (templateRepository.existsByCode(request.code())) {
            throw new DuplicateResourceException("A template with code '" + request.code() + "' already exists");
        }
        if (templateRepository.existsByWorkflowIdAndDisplayOrder(workflowId, request.displayOrder())) {
            throw new DuplicateResourceException(
                    "A template with displayOrder " + request.displayOrder()
                            + " already exists in this workflow");
        }

        Template template = new Template();
        template.setWorkflow(workflow);
        template.setCode(request.code());
        template.setName(request.name());
        template.setDescription(request.description());
        template.setDisplayOrder(request.displayOrder());
        template.setTemplateRule(jsonColumnMapper.write(request.templateRule()));
        LocalDateTime now = LocalDateTime.now();
        template.setCreatedAt(now);
        template.setUpdatedAt(now);

        return toResponse(templateRepository.save(template));
    }

    public List<TemplateResponse> getAllByWorkflow(UUID workflowId) {
        findWorkflowOrThrow(workflowId);
        return templateRepository.findAllByWorkflowIdOrderByDisplayOrderAsc(workflowId).stream()
                .map(this::toResponse)
                .toList();
    }

    public TemplateResponse getById(UUID id) {
        return toResponse(findTemplateOrThrow(id));
    }

    @Transactional
    public TemplateResponse update(UUID id, TemplateUpdateRequest request) {
        Template template = findTemplateOrThrow(id);
        if (templateRepository.existsByWorkflowIdAndDisplayOrderAndIdNot(
                template.getWorkflow().getId(), request.displayOrder(), id)) {
            throw new DuplicateResourceException(
                    "A template with displayOrder " + request.displayOrder()
                            + " already exists in this workflow");
        }
        template.setName(request.name());
        template.setDescription(request.description());
        template.setDisplayOrder(request.displayOrder());
        template.setTemplateRule(jsonColumnMapper.write(request.templateRule()));
        template.setUpdatedAt(LocalDateTime.now());

        return toResponse(template);
    }

    @Transactional
    public void delete(UUID id) {
        Template template = findTemplateOrThrow(id);
        if (templateRepository.hasQuestions(id)) {
            throw new ResourceInUseException(
                    "Template '" + template.getCode() + "' still has questions and cannot be deleted");
        }
        templateRepository.delete(template);
    }

    private Template findTemplateOrThrow(UUID id) {
        return templateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Template " + id + " not found"));
    }

    private Workflow findWorkflowOrThrow(UUID workflowId) {
        return workflowRepository.findById(workflowId)
                .orElseThrow(() -> new ResourceNotFoundException("Workflow " + workflowId + " not found"));
    }

    private TemplateResponse toResponse(Template template) {
        return TemplateResponse.from(template, jsonColumnMapper);
    }
}
