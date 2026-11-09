package com.questionnaire.questionnaire_platform.service;

import com.questionnaire.questionnaire_platform.dto.QuestionCreateRequest;
import com.questionnaire.questionnaire_platform.dto.QuestionDto;
import com.questionnaire.questionnaire_platform.dto.TemplateCreateRequest;
import com.questionnaire.questionnaire_platform.dto.TemplateResponse;
import com.questionnaire.questionnaire_platform.dto.WorkflowCreateRequest;
import com.questionnaire.questionnaire_platform.dto.WorkflowResponse;
import com.questionnaire.questionnaire_platform.entity.QuestionType;
import com.questionnaire.questionnaire_platform.entity.SubjectScope;
import com.questionnaire.questionnaire_platform.exception.DuplicateResourceException;
import com.questionnaire.questionnaire_platform.exception.InvalidConfigurationException;
import com.questionnaire.questionnaire_platform.exception.ResourceInUseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Covers: duplicate-prevention across Workflow/Template/Question (this IS "seed data can be
// loaded repeatedly without creating duplicates" - re-running a setup script against existing
// config is rejected, never silently duplicated), delete-blocked-if-referenced at every level
// of the hierarchy, and ConfigurationValidator actually wired into the real service layer (not
// just exercised in isolation, as ConfigurationValidatorTest already covers at the unit level).
@SpringBootTest
@Transactional
class AdminConfigurationIntegrationTest {

    @Autowired
    private WorkflowService workflowService;
    @Autowired
    private TemplateService templateService;
    @Autowired
    private QuestionService questionService;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    private UUID workflowId;

    @BeforeEach
    void seedWorkflow() {
        workflowId = workflowService.create(new WorkflowCreateRequest("WF", "WF", null, 1)).id();
    }

    @Test
    void reRunningTheSameWorkflowSeedIsRejectedNotDuplicated() {
        assertThatThrownBy(() -> workflowService.create(new WorkflowCreateRequest("WF", "WF", null, 2)))
                .isInstanceOf(DuplicateResourceException.class);
        assertThatThrownBy(() -> workflowService.create(new WorkflowCreateRequest("WF2", "WF2", null, 1)))
                .isInstanceOf(DuplicateResourceException.class);

        assertThat(workflowService.getAll()).hasSize(1);
    }

    @Test
    void reRunningTheSameTemplateSeedIsRejectedNotDuplicated() {
        templateService.create(workflowId, new TemplateCreateRequest("TPL", "TPL", null, 1, null, SubjectScope.PROBAND_ONLY));

        assertThatThrownBy(() -> templateService.create(workflowId,
                new TemplateCreateRequest("TPL", "TPL", null, 2, null, SubjectScope.PROBAND_ONLY)))
                .isInstanceOf(DuplicateResourceException.class);
        assertThatThrownBy(() -> templateService.create(workflowId,
                new TemplateCreateRequest("TPL2", "TPL2", null, 1, null, SubjectScope.PROBAND_ONLY)))
                .isInstanceOf(DuplicateResourceException.class);

        assertThat(templateService.getAllByWorkflow(workflowId)).hasSize(1);
    }

    @Test
    void reRunningTheSameQuestionSeedIsRejectedNotDuplicated() {
        UUID templateId = templateService.create(workflowId,
                new TemplateCreateRequest("TPL", "TPL", null, 1, null, SubjectScope.PROBAND_ONLY)).id();
        questionService.create(templateId, new QuestionCreateRequest("q1", "Q1", QuestionType.TEXT, 1, null, null, null, null));

        assertThatThrownBy(() -> questionService.create(templateId,
                new QuestionCreateRequest("q1", "Q1 again", QuestionType.TEXT, 2, null, null, null, null)))
                .isInstanceOf(DuplicateResourceException.class);

        assertThat(questionService.getAllByTemplate(templateId)).hasSize(1);
    }

    @Test
    void creatingAStructuredQuestionPersistsAndReturnsItsFieldConfig() {
        JsonNode fieldConfig = objectMapper.readTree("""
                {"shape":"list","fields":[
                  {"name":"diseaseType","label":"Disease name","type":"TEXT","required":true},
                  {"name":"ageAtDiagnosis","label":"Age at diagnosis","type":"NUMBER","required":false}
                ]}""");
        UUID templateId = templateService.create(workflowId,
                new TemplateCreateRequest("TPL", "TPL", null, 1, null, SubjectScope.PROBAND_ONLY)).id();
        QuestionDto created = questionService.create(templateId,
                new QuestionCreateRequest("diseases_test", "Diseases", QuestionType.STRUCTURED, 1, null, null, null, fieldConfig));

        // Force a real round trip to Postgres rather than reading back from the first-level cache -
        // this is exactly what would have caught the missing "ALTER TYPE question_type ADD VALUE"
        // migration (found instead, later, by StructuredQuestionIntegrationTest).
        entityManager.flush();
        entityManager.clear();

        QuestionDto fetched = questionService.getById(created.id());
        assertThat(fetched.type()).isEqualTo(QuestionType.STRUCTURED);
        assertThat(fetched.fieldConfig().path("shape").asString()).isEqualTo("list");
        assertThat(fetched.fieldConfig().path("fields").get(0).path("name").asString()).isEqualTo("diseaseType");
    }

    @Test
    void workflowDeleteIsBlockedWhileItStillHasTemplates() {
        templateService.create(workflowId, new TemplateCreateRequest("TPL", "TPL", null, 1, null, SubjectScope.PROBAND_ONLY));
        assertThatThrownBy(() -> workflowService.delete(workflowId)).isInstanceOf(ResourceInUseException.class);
    }

    @Test
    void templateDeleteIsBlockedWhileItStillHasQuestions() {
        UUID templateId = templateService.create(workflowId,
                new TemplateCreateRequest("TPL", "TPL", null, 1, null, SubjectScope.PROBAND_ONLY)).id();
        questionService.create(templateId, new QuestionCreateRequest("q1", "Q1", QuestionType.TEXT, 1, null, null, null, null));

        assertThatThrownBy(() -> templateService.delete(templateId)).isInstanceOf(ResourceInUseException.class);
    }

    @Test
    void emptyWorkflowAndTemplateDeleteCleanly() {
        UUID templateId = templateService.create(workflowId,
                new TemplateCreateRequest("TPL", "TPL", null, 1, null, SubjectScope.PROBAND_ONLY)).id();

        assertThatCode(() -> templateService.delete(templateId)).doesNotThrowAnyException();
        assertThatCode(() -> workflowService.delete(workflowId)).doesNotThrowAnyException();
    }

    @Test
    void malformedTemplateRuleIsRejectedByTheRealServiceNotJustTheUnitTest() {
        assertThatThrownBy(() -> templateService.create(workflowId, new TemplateCreateRequest(
                        "TPL", "TPL", null, 1, objectMapper.readTree("{\"notAnOperator\":[1]}"), SubjectScope.PROBAND_ONLY)))
                .isInstanceOf(InvalidConfigurationException.class);
    }

    @Test
    void malformedQuestionValidationIsRejectedByTheRealServiceNotJustTheUnitTest() {
        UUID templateId = templateService.create(workflowId,
                new TemplateCreateRequest("TPL", "TPL", null, 1, null, SubjectScope.PROBAND_ONLY)).id();

        assertThatThrownBy(() -> questionService.create(templateId, new QuestionCreateRequest(
                        "q1", "Q1", QuestionType.TEXT, 1, null, objectMapper.readTree("{\"minLength\":10,\"maxLength\":5}"), null, null)))
                .isInstanceOf(InvalidConfigurationException.class);
    }

    @Test
    void updatingAWorkflowPersistsTheChange() {
        WorkflowResponse updated = workflowService.update(workflowId,
                new com.questionnaire.questionnaire_platform.dto.WorkflowUpdateRequest("Renamed", "New description", 5));

        assertThat(updated.name()).isEqualTo("Renamed");
        assertThat(updated.displayOrder()).isEqualTo(5);
        assertThat(workflowService.getById(workflowId).name()).isEqualTo("Renamed");
    }

    @Test
    void updatingAQuestionReValidatesItsConfiguration() {
        UUID templateId = templateService.create(workflowId,
                new TemplateCreateRequest("TPL", "TPL", null, 1, null, SubjectScope.PROBAND_ONLY)).id();
        UUID questionId = questionService.create(templateId,
                new QuestionCreateRequest("q1", "Q1", QuestionType.TEXT, 1, null, null, null, null)).id();

        assertThatThrownBy(() -> questionService.update(questionId, new com.questionnaire.questionnaire_platform.dto.QuestionUpdateRequest(
                        "Q1 updated", QuestionType.TEXT, 1, null, objectMapper.readTree("{\"minLength\":10,\"maxLength\":5}"), null, null)))
                .isInstanceOf(InvalidConfigurationException.class);

        var updated = questionService.update(questionId, new com.questionnaire.questionnaire_platform.dto.QuestionUpdateRequest(
                "Q1 updated", QuestionType.TEXT, 1, null, objectMapper.readTree("{\"required\":true}"), null, null));
        assertThat(updated.text()).isEqualTo("Q1 updated");
    }

    @Test
    void updatingATemplateReValidatesTheRule() {
        TemplateResponse template = templateService.create(workflowId,
                new TemplateCreateRequest("TPL", "TPL", null, 1, null, SubjectScope.PROBAND_ONLY));

        assertThatThrownBy(() -> templateService.update(template.id(), new com.questionnaire.questionnaire_platform.dto.TemplateUpdateRequest(
                        "TPL", null, 1, objectMapper.readTree("{\"notAnOperator\":[1]}"), SubjectScope.PROBAND_ONLY)))
                .isInstanceOf(InvalidConfigurationException.class);
    }
}
