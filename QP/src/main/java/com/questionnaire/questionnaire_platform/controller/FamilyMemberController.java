package com.questionnaire.questionnaire_platform.controller;

import com.questionnaire.questionnaire_platform.dto.FamilyMemberCreateRequest;
import com.questionnaire.questionnaire_platform.dto.FamilyMemberResponse;
import com.questionnaire.questionnaire_platform.dto.RelativeCountReconciliation;
import com.questionnaire.questionnaire_platform.service.FamilyMemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/assessment/{sessionId}/family-members")
@RequiredArgsConstructor
public class FamilyMemberController {

    private final FamilyMemberService familyMemberService;

    // Only adds the family member to the list - it does NOT start their pass. The proband
    // continues into their walk the same way as any other already-added family member: via
    // GET .../current-template?familyMemberId=..., which lazily resolves their entry template the
    // first time it's called for them. See
    // docs/superpowers/specs/2026-10-29-auth-removal-and-independent-passes-design.md §3.
    @PostMapping
    public ResponseEntity<FamilyMemberResponse> create(@PathVariable UUID sessionId,
                                                          @Valid @RequestBody FamilyMemberCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(familyMemberService.create(sessionId, request));
    }

    @GetMapping
    public ResponseEntity<List<FamilyMemberResponse>> getAll(@PathVariable UUID sessionId) {
        return ResponseEntity.ok(familyMemberService.getAll(sessionId));
    }

    // FR-13.2/13.3: compares declared relative counts against actual FAMILY_MEMBER rows. A
    // literal path segment, resolved by Spring before the /{familyMemberId} variable mapping below.
    @GetMapping("/reconciliation")
    public ResponseEntity<List<RelativeCountReconciliation>> reconcileRelativeCounts(@PathVariable UUID sessionId) {
        return ResponseEntity.ok(familyMemberService.reconcileRelativeCounts(sessionId));
    }

    @GetMapping("/{familyMemberId}")
    public ResponseEntity<FamilyMemberResponse> getById(@PathVariable UUID sessionId,
                                                           @PathVariable UUID familyMemberId) {
        return ResponseEntity.ok(familyMemberService.getById(sessionId, familyMemberId));
    }

    @DeleteMapping("/{familyMemberId}")
    public ResponseEntity<Void> delete(@PathVariable UUID sessionId, @PathVariable UUID familyMemberId) {
        familyMemberService.delete(sessionId, familyMemberId);
        return ResponseEntity.noContent().build();
    }
}
