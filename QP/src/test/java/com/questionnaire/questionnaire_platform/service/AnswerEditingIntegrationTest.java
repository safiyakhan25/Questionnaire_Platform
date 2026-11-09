package com.questionnaire.questionnaire_platform.service;

import com.questionnaire.questionnaire_platform.dto.AnswerSubmitRequest;
import com.questionnaire.questionnaire_platform.dto.AssessmentStartRequest;
import com.questionnaire.questionnaire_platform.dto.CurrentTemplateResponse;
import com.questionnaire.questionnaire_platform.dto.QuestionResponseView;
import com.questionnaire.questionnaire_platform.dto.VisitedTemplateResponse;
import com.questionnaire.questionnaire_platform.entity.QuestionType;
import com.questionnaire.questionnaire_platform.entity.SubjectScope;
import com.questionnaire.questionnaire_platform.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

import static com.questionnaire.questionnaire_platform.support.TestFixtures.createQuestion;
import static com.questionnaire.questionnaire_platform.support.TestFixtures.createTemplate;
import static com.questionnaire.questionnaire_platform.support.TestFixtures.createWorkflow;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Covers FR-8 (previous/forward navigation) and FR-9 (editing with downstream invalidation).
@SpringBootTest
@Transactional
class AnswerEditingIntegrationTest {

    @Autowired
    private WorkflowService workflowService;
    @Autowired
    private TemplateService templateService;
    @Autowired
    private QuestionService questionService;
    @Autowired
    private AssessmentService assessmentService;
    @Autowired
    private ObjectMapper objectMapper;

    private UUID basicTemplateId;
    private UUID genderQuestionId;
    private UUID isPregnantQuestionId;

    @BeforeEach
    void seedWorkflow() {
        UUID workflowId = createWorkflow(workflowService, "TEST_WF", 1);
        basicTemplateId = createTemplate(templateService, workflowId, "BASIC", 1, null, SubjectScope.PROBAND_ONLY);
        genderQuestionId = createQuestion(questionService, basicTemplateId, "gender_at_birth", QuestionType.RADIO, 1,
                objectMapper.readTree("[{\"label\":\"Male\",\"value\":\"MALE\"},{\"label\":\"Female\",\"value\":\"FEMALE\"}]"),
                null, null);

        UUID womenTemplateId = createTemplate(templateService, workflowId, "WOMEN", 2,
                objectMapper.readTree("{\"==\":[{\"var\":\"gender_at_birth\"},\"FEMALE\"]}"), SubjectScope.PROBAND_ONLY);
        isPregnantQuestionId = createQuestion(questionService, womenTemplateId, "is_pregnant", QuestionType.RADIO, 1,
                objectMapper.readTree("[{\"label\":\"Yes\",\"value\":true},{\"label\":\"No\",\"value\":false}]"),
                objectMapper.readTree("{\"required\":true}"), null);
    }

    private UUID startSession() {
        return assessmentService.start(new AssessmentStartRequest(null)).sessionId();
    }

    private void answer(UUID sessionId, UUID questionId, String jsonValue) {
        assessmentService.submitAnswer(sessionId, new AnswerSubmitRequest(questionId, objectMapper.readTree(jsonValue), null));
    }

    @Test
    void browsingAVisitedTemplateDoesNotMoveTheRealPosition() {
        UUID sessionId = startSession();
        answer(sessionId, genderQuestionId, "\"FEMALE\"");
        assessmentService.nextTemplate(sessionId, null); // real position now WOMEN

        assessmentService.viewVisitedTemplate(sessionId, basicTemplateId, null); // read-only browse

        CurrentTemplateResponse current = assessmentService.currentTemplate(sessionId, null);
        assertThat(current.templateCode()).isEqualTo("WOMEN");
    }

    @Test
    void browsingAnUnvisitedTemplateIsRejected() {
        UUID sessionId = startSession();
        UUID neverVisitedTemplateId = UUID.randomUUID();
        assertThatThrownBy(() -> assessmentService.viewVisitedTemplate(sessionId, neverVisitedTemplateId, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void visitedTemplatesListsOnlyAnsweredTemplatesInOrder() {
        UUID sessionId = startSession();
        answer(sessionId, genderQuestionId, "\"FEMALE\"");
        assessmentService.nextTemplate(sessionId, null);
        answer(sessionId, isPregnantQuestionId, "false");

        List<VisitedTemplateResponse> visited = assessmentService.visitedTemplates(sessionId, null);
        assertThat(visited).extracting(VisitedTemplateResponse::templateCode).containsExactly("BASIC", "WOMEN");
    }

    @Test
    void resumeAtTemplateMovesTheRealPositionEnablingAnEdit() {
        UUID sessionId = startSession();
        answer(sessionId, genderQuestionId, "\"FEMALE\"");
        assessmentService.nextTemplate(sessionId, null);
        answer(sessionId, isPregnantQuestionId, "false");

        assessmentService.resumeAtTemplate(sessionId, basicTemplateId, null);
        CurrentTemplateResponse current = assessmentService.currentTemplate(sessionId, null);
        assertThat(current.templateCode()).isEqualTo("BASIC");
    }

    @Test
    void editingAnEarlierAnswerCascadesInvalidationToDownstreamData() {
        UUID sessionId = startSession();
        answer(sessionId, genderQuestionId, "\"FEMALE\"");
        assessmentService.nextTemplate(sessionId, null);
        answer(sessionId, isPregnantQuestionId, "false");

        assessmentService.resumeAtTemplate(sessionId, basicTemplateId, null);
        QuestionResponseView editResponse = assessmentService.submitAnswer(sessionId,
                new AnswerSubmitRequest(genderQuestionId, objectMapper.readTree("\"MALE\""), null));

        assertThat(editResponse.invalidatedAnswerKeys()).containsExactly("is_pregnant");

        // The edit is correctly reflected for rule evaluation: WOMEN is no longer reachable.
        CurrentTemplateResponse next = assessmentService.nextTemplate(sessionId, null);
        assertThat(next.completed()).isTrue();

        // But the full history, including the now-superseded FEMALE/is_pregnant answers, is untouched.
        List<QuestionResponseView> responses = assessmentService.responses(sessionId);
        assertThat(responses).extracting(QuestionResponseView::answerKey)
                .contains("gender_at_birth", "is_pregnant");
        assertThat(responses).hasSize(3); // FEMALE, is_pregnant=false, MALE - all three preserved
    }

    // Final-review finding: a template with zero questions (like FAMILY_MEMBER_HUB) can never
    // acquire a QUESTION_RESPONSE row, so the answer-based "visited" tracking can never surface it -
    // meaning the proband could never navigate back to it once their own walk moved past it, despite
    // the design's explicit promise that "the proband can always jump back to it later". A
    // zero-question template is purely a navigation waypoint; it must stay reachable regardless of
    // answer history.
    @Test
    void aZeroQuestionNavigationTemplateRemainsReachableAfterTheProbandMovesPastIt() {
        UUID hubWorkflowId = createWorkflow(workflowService, "HUB_WF", 10);
        UUID hubTemplateId = createTemplate(templateService, hubWorkflowId, "HUB", 1, null, SubjectScope.PROBAND_ONLY);

        UUID sessionId = startSession();
        answer(sessionId, genderQuestionId, "\"MALE\""); // skips WOMEN
        CurrentTemplateResponse afterBasic = assessmentService.nextTemplate(sessionId, null);
        assertThat(afterBasic.templateCode()).isEqualTo("HUB");

        assessmentService.nextTemplate(sessionId, null); // past HUB - session completes
        assertThat(assessmentService.status(sessionId).status().name()).isEqualTo("COMPLETED");

        CurrentTemplateResponse backAtHub = assessmentService.resumeAtTemplate(sessionId, hubTemplateId, null);
        assertThat(backAtHub.templateCode()).isEqualTo("HUB");

        assertThat(assessmentService.visitedTemplates(sessionId, null))
                .extracting(VisitedTemplateResponse::templateCode)
                .contains("HUB");
    }

    @Test
    void resumeAtTemplateUncompletesASession() {
        UUID sessionId = startSession();
        answer(sessionId, genderQuestionId, "\"MALE\"");
        assessmentService.nextTemplate(sessionId, null);
        assertThat(assessmentService.status(sessionId).status().name()).isEqualTo("COMPLETED");

        assessmentService.resumeAtTemplate(sessionId, basicTemplateId, null);
        assertThat(assessmentService.status(sessionId).status().name()).isEqualTo("IN_PROGRESS");
    }
}
