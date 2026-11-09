package com.questionnaire.questionnaire_platform.controller;

import com.questionnaire.questionnaire_platform.dto.PartnerRelationshipCreateRequest;
import com.questionnaire.questionnaire_platform.dto.PartnerRelationshipResponse;
import com.questionnaire.questionnaire_platform.service.PartnerRelationshipService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/assessment/{sessionId}/relationships/partners")
@RequiredArgsConstructor
public class PartnerRelationshipController {

    private final PartnerRelationshipService partnerRelationshipService;

    @PostMapping
    public ResponseEntity<PartnerRelationshipResponse> create(@PathVariable UUID sessionId,
                                                                 @Valid @RequestBody PartnerRelationshipCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(partnerRelationshipService.create(sessionId, request));
    }

    @GetMapping
    public ResponseEntity<List<PartnerRelationshipResponse>> getAll(@PathVariable UUID sessionId) {
        return ResponseEntity.ok(partnerRelationshipService.getAll(sessionId));
    }

    @GetMapping("/{relationshipId}")
    public ResponseEntity<PartnerRelationshipResponse> getById(@PathVariable UUID sessionId,
                                                                  @PathVariable UUID relationshipId) {
        return ResponseEntity.ok(partnerRelationshipService.getById(sessionId, relationshipId));
    }

    @DeleteMapping("/{relationshipId}")
    public ResponseEntity<Void> delete(@PathVariable UUID sessionId, @PathVariable UUID relationshipId) {
        partnerRelationshipService.delete(sessionId, relationshipId);
        return ResponseEntity.noContent().build();
    }
}
