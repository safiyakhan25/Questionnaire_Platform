package com.questionnaire.questionnaire_platform.support;

import com.questionnaire.questionnaire_platform.dto.QuestionCreateRequest;
import com.questionnaire.questionnaire_platform.dto.TemplateCreateRequest;
import com.questionnaire.questionnaire_platform.dto.WorkflowCreateRequest;
import com.questionnaire.questionnaire_platform.entity.QuestionType;
import com.questionnaire.questionnaire_platform.entity.SubjectScope;
import com.questionnaire.questionnaire_platform.service.QuestionService;
import com.questionnaire.questionnaire_platform.service.TemplateService;
import com.questionnaire.questionnaire_platform.service.WorkflowService;
import tools.jackson.databind.JsonNode;

import java.util.UUID;

// Shared fixture builders for integration tests - goes through the real admin service layer
// (not raw repository inserts), so fixture setup also exercises ConfigurationValidator.
public final class TestFixtures {

    private TestFixtures() {
    }

    public static UUID createWorkflow(WorkflowService workflowService, String code, int displayOrder) {
        return workflowService.create(new WorkflowCreateRequest(code, code, null, displayOrder)).id();
    }

    public static UUID createTemplate(TemplateService templateService, UUID workflowId, String code, int displayOrder,
                                       JsonNode templateRule, SubjectScope subjectScope) {
        return templateService.create(workflowId,
                new TemplateCreateRequest(code, code, null, displayOrder, templateRule, subjectScope)).id();
    }

    public static UUID createQuestion(QuestionService questionService, UUID templateId, String answerKey, QuestionType type,
                                       int displayOrder, JsonNode options, JsonNode validation, JsonNode visibilityRule) {
        return createQuestion(questionService, templateId, answerKey, type, displayOrder, options, validation, visibilityRule, null);
    }

    public static UUID createQuestion(QuestionService questionService, UUID templateId, String answerKey, QuestionType type,
                                       int displayOrder, JsonNode options, JsonNode validation, JsonNode visibilityRule,
                                       JsonNode fieldConfig) {
        return questionService.create(templateId,
                new QuestionCreateRequest(answerKey, answerKey, type, displayOrder, options, validation, visibilityRule, fieldConfig)).id();
    }
}
