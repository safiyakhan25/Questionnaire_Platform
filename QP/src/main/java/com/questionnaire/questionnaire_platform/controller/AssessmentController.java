package com.questionnaire.questionnaire_platform.controller;

import com.questionnaire.questionnaire_platform.dto.AnswerSubmitRequest;
import com.questionnaire.questionnaire_platform.dto.AssessmentStartRequest;
import com.questionnaire.questionnaire_platform.dto.CurrentTemplateResponse;
import com.questionnaire.questionnaire_platform.dto.QuestionResponseView;
import com.questionnaire.questionnaire_platform.dto.SessionStatusResponse;
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

    @GetMapping("/{sessionId}/current-template")
    public ResponseEntity<CurrentTemplateResponse> currentTemplate(@PathVariable UUID sessionId) {
        return ResponseEntity.ok(assessmentService.currentTemplate(sessionId));
    }

    @PostMapping("/{sessionId}/answer")
    public ResponseEntity<QuestionResponseView> submitAnswer(@PathVariable UUID sessionId,
                                                               @Valid @RequestBody AnswerSubmitRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(assessmentService.submitAnswer(sessionId, request));
    }

    @PostMapping("/{sessionId}/next-template")
    public ResponseEntity<CurrentTemplateResponse> nextTemplate(@PathVariable UUID sessionId) {
        return ResponseEntity.ok(assessmentService.nextTemplate(sessionId));
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<SessionStatusResponse> status(@PathVariable UUID sessionId) {
        return ResponseEntity.ok(assessmentService.status(sessionId));
    }

    @GetMapping("/{sessionId}/responses")
    public ResponseEntity<List<QuestionResponseView>> responses(@PathVariable UUID sessionId) {
        return ResponseEntity.ok(assessmentService.responses(sessionId));
    }
}
