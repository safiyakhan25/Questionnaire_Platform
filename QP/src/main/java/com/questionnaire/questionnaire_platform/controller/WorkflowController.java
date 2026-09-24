package com.questionnaire.questionnaire_platform.controller;

import com.questionnaire.questionnaire_platform.dto.WorkflowCreateRequest;
import com.questionnaire.questionnaire_platform.dto.WorkflowResponse;
import com.questionnaire.questionnaire_platform.dto.WorkflowUpdateRequest;
import com.questionnaire.questionnaire_platform.service.WorkflowService;
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
public class WorkflowController {

    private final WorkflowService workflowService;

    @PostMapping("/workflows")
    public ResponseEntity<WorkflowResponse> create(@Valid @RequestBody WorkflowCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(workflowService.create(request));
    }

    @GetMapping("/workflows")
    public ResponseEntity<List<WorkflowResponse>> getAll() {
        return ResponseEntity.ok(workflowService.getAll());
    }

    @GetMapping("/workflow/{id}")
    public ResponseEntity<WorkflowResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(workflowService.getById(id));
    }

    @PutMapping("/workflow/{id}")
    public ResponseEntity<WorkflowResponse> update(@PathVariable UUID id,
                                                     @Valid @RequestBody WorkflowUpdateRequest request) {
        return ResponseEntity.ok(workflowService.update(id, request));
    }

    @DeleteMapping("/workflow/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        workflowService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
