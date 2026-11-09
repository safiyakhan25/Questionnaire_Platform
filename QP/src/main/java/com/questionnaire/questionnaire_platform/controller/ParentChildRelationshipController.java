package com.questionnaire.questionnaire_platform.controller;

import com.questionnaire.questionnaire_platform.dto.ParentChildRelationshipCreateRequest;
import com.questionnaire.questionnaire_platform.dto.ParentChildRelationshipResponse;
import com.questionnaire.questionnaire_platform.service.ParentChildRelationshipService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/assessment/{sessionId}/relationships/parent-child")
@RequiredArgsConstructor
public class ParentChildRelationshipController {

    private final ParentChildRelationshipService parentChildRelationshipService;

    @PostMapping
    public ResponseEntity<ParentChildRelationshipResponse> create(@PathVariable UUID sessionId,
                                                                     @Valid @RequestBody ParentChildRelationshipCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(parentChildRelationshipService.create(sessionId, request));
    }

    @GetMapping
    public ResponseEntity<List<ParentChildRelationshipResponse>> getAll(@PathVariable UUID sessionId) {
        return ResponseEntity.ok(parentChildRelationshipService.getAll(sessionId));
    }

    @GetMapping("/{relationshipId}")
    public ResponseEntity<ParentChildRelationshipResponse> getById(@PathVariable UUID sessionId,
                                                                      @PathVariable UUID relationshipId) {
        return ResponseEntity.ok(parentChildRelationshipService.getById(sessionId, relationshipId));
    }

    @DeleteMapping("/{relationshipId}")
    public ResponseEntity<Void> delete(@PathVariable UUID sessionId, @PathVariable UUID relationshipId) {
        parentChildRelationshipService.delete(sessionId, relationshipId);
        return ResponseEntity.noContent().build();
    }
}
