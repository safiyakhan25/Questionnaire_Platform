package com.questionnaire.questionnaire_platform.controller;

import com.questionnaire.questionnaire_platform.dto.TemplateCreateRequest;
import com.questionnaire.questionnaire_platform.dto.TemplateResponse;
import com.questionnaire.questionnaire_platform.dto.TemplateUpdateRequest;
import com.questionnaire.questionnaire_platform.service.TemplateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class TemplateController {

    private final TemplateService templateService;

    @PostMapping("/workflow/{workflowId}/templates")
    public ResponseEntity<TemplateResponse> create(@PathVariable UUID workflowId,
                                                     @Valid @RequestBody TemplateCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(templateService.create(workflowId, request));
    }

    @GetMapping("/workflow/{workflowId}/templates")
    public ResponseEntity<List<TemplateResponse>> getAllByWorkflow(@PathVariable UUID workflowId) {
        return ResponseEntity.ok(templateService.getAllByWorkflow(workflowId));
    }

    @GetMapping("/template/{templateId}")
    public ResponseEntity<TemplateResponse> getById(@PathVariable UUID templateId) {
        return ResponseEntity.ok(templateService.getById(templateId));
    }

    @PutMapping("/template/{templateId}")
    public ResponseEntity<TemplateResponse> update(@PathVariable UUID templateId,
                                                     @Valid @RequestBody TemplateUpdateRequest request) {
        return ResponseEntity.ok(templateService.update(templateId, request));
    }

    @DeleteMapping("/template/{templateId}")
    public ResponseEntity<Void> delete(@PathVariable UUID templateId) {
        templateService.delete(templateId);
        return ResponseEntity.noContent().build();
    }
}
