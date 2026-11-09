package com.questionnaire.questionnaire_platform.controller;

import com.questionnaire.questionnaire_platform.dto.AnswerSubmitRequest;
import com.questionnaire.questionnaire_platform.dto.AssessmentStartRequest;
import com.questionnaire.questionnaire_platform.dto.CurrentTemplateResponse;
import com.questionnaire.questionnaire_platform.dto.QuestionResponseView;
import com.questionnaire.questionnaire_platform.dto.SessionStatusResponse;
import com.questionnaire.questionnaire_platform.dto.SessionSummaryResponse;
import com.questionnaire.questionnaire_platform.dto.VisitedTemplateResponse;
import com.questionnaire.questionnaire_platform.service.AssessmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/assessment")
@RequiredArgsConstructor
public class AssessmentController {

    private final AssessmentService assessmentService;

    @PostMapping("/start")
    public ResponseEntity<CurrentTemplateResponse> start(@RequestBody(required = false) AssessmentStartRequest request) {
        AssessmentStartRequest effective = request != null ? request : new AssessmentStartRequest(null);
        return ResponseEntity.ok(assessmentService.start(effective));
    }

    // For the landing page's session list. A literal path segment, resolved by Spring before the
    // /{sessionId} variable mapping below - same reasoning as FamilyMemberController's /reconciliation.
    @GetMapping("/sessions")
    public ResponseEntity<List<SessionSummaryResponse>> sessions() {
        return ResponseEntity.ok(assessmentService.listSessions());
    }

    @GetMapping("/{sessionId}/current-template")
    public ResponseEntity<CurrentTemplateResponse> currentTemplate(@PathVariable UUID sessionId,
                                                                      @RequestParam(required = false) UUID familyMemberId) {
        return ResponseEntity.ok(assessmentService.currentTemplate(sessionId, familyMemberId));
    }

    @PostMapping("/{sessionId}/answer")
    public ResponseEntity<QuestionResponseView> submitAnswer(@PathVariable UUID sessionId,
                                                               @Valid @RequestBody AnswerSubmitRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(assessmentService.submitAnswer(sessionId, request));
    }

    @PostMapping("/{sessionId}/next-template")
    public ResponseEntity<CurrentTemplateResponse> nextTemplate(@PathVariable UUID sessionId,
                                                                   @RequestParam(required = false) UUID familyMemberId) {
        return ResponseEntity.ok(assessmentService.nextTemplate(sessionId, familyMemberId));
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<SessionStatusResponse> status(@PathVariable UUID sessionId) {
        return ResponseEntity.ok(assessmentService.status(sessionId));
    }

    @GetMapping("/{sessionId}/responses")
    public ResponseEntity<List<QuestionResponseView>> responses(@PathVariable UUID sessionId) {
        return ResponseEntity.ok(assessmentService.responses(sessionId));
    }

    // FR-8.1/8.2: the named subject's actual answered path, for rendering Previous/Forward UI.
    @GetMapping("/{sessionId}/visited-templates")
    public ResponseEntity<List<VisitedTemplateResponse>> visitedTemplates(@PathVariable UUID sessionId,
                                                                             @RequestParam(required = false) UUID familyMemberId) {
        return ResponseEntity.ok(assessmentService.visitedTemplates(sessionId, familyMemberId));
    }

    // FR-8.3/8.5: read-only browsing of one previously visited template - never moves the real
    // position, never submits or modifies anything.
    @GetMapping("/{sessionId}/templates/{templateId}")
    public ResponseEntity<CurrentTemplateResponse> viewVisitedTemplate(@PathVariable UUID sessionId,
                                                                          @PathVariable UUID templateId,
                                                                          @RequestParam(required = false) UUID familyMemberId) {
        return ResponseEntity.ok(assessmentService.viewVisitedTemplate(sessionId, templateId, familyMemberId));
    }

    // FR-8.4/8.6: "go back to here" - moves the named subject's real position to templateId, the
    // only path by which editing (FR-9, via the normal /answer endpoint afterward) becomes possible.
    @PostMapping("/{sessionId}/templates/{templateId}/resume-here")
    public ResponseEntity<CurrentTemplateResponse> resumeAtTemplate(@PathVariable UUID sessionId,
                                                                       @PathVariable UUID templateId,
                                                                       @RequestParam(required = false) UUID familyMemberId) {
        return ResponseEntity.ok(assessmentService.resumeAtTemplate(sessionId, templateId, familyMemberId));
    }
}
