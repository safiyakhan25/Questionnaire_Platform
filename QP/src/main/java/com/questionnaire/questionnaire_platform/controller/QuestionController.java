package com.questionnaire.questionnaire_platform.controller;

import com.questionnaire.questionnaire_platform.dto.QuestionCreateRequest;
import com.questionnaire.questionnaire_platform.dto.QuestionDto;
import com.questionnaire.questionnaire_platform.dto.QuestionUpdateRequest;
import com.questionnaire.questionnaire_platform.service.QuestionService;
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
public class QuestionController {

    private final QuestionService questionService;

    @PostMapping("/template/{templateId}/questions")
    public ResponseEntity<QuestionDto> create(@PathVariable UUID templateId,
                                               @Valid @RequestBody QuestionCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(questionService.create(templateId, request));
    }

    @GetMapping("/template/{templateId}/questions")
    public ResponseEntity<List<QuestionDto>> getAllByTemplate(@PathVariable UUID templateId) {
        return ResponseEntity.ok(questionService.getAllByTemplate(templateId));
    }

    @GetMapping("/question/{questionId}")
    public ResponseEntity<QuestionDto> getById(@PathVariable UUID questionId) {
        return ResponseEntity.ok(questionService.getById(questionId));
    }

    @PutMapping("/question/{questionId}")
    public ResponseEntity<QuestionDto> update(@PathVariable UUID questionId,
                                               @Valid @RequestBody QuestionUpdateRequest request) {
        return ResponseEntity.ok(questionService.update(questionId, request));
    }

    @DeleteMapping("/question/{questionId}")
    public ResponseEntity<Void> delete(@PathVariable UUID questionId) {
        questionService.delete(questionId);
        return ResponseEntity.noContent().build();
    }
}
