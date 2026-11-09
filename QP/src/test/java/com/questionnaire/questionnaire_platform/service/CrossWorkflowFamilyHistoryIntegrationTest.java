package com.questionnaire.questionnaire_platform.service;

import com.questionnaire.questionnaire_platform.dto.AnswerSubmitRequest;
import com.questionnaire.questionnaire_platform.dto.AssessmentStartRequest;
import com.questionnaire.questionnaire_platform.dto.CurrentTemplateResponse;
import com.questionnaire.questionnaire_platform.dto.FamilyMemberCreateRequest;
import com.questionnaire.questionnaire_platform.dto.FamilyMemberResponse;
import com.questionnaire.questionnaire_platform.entity.MaternalPaternalSideType;
import com.questionnaire.questionnaire_platform.entity.QuestionType;
import com.questionnaire.questionnaire_platform.entity.RelationshipType;
import com.questionnaire.questionnaire_platform.entity.SubjectScope;
import com.questionnaire.questionnaire_platform.exception.InvalidAnswerException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static com.questionnaire.questionnaire_platform.support.TestFixtures.createQuestion;
import static com.questionnaire.questionnaire_platform.support.TestFixtures.createTemplate;
import static com.questionnaire.questionnaire_platform.support.TestFixtures.createWorkflow;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Directly answers two verification questions raised about the proposed Personal
// Details -> Family History question set, using that exact 2-workflow structure (not the
// single-workflow fixtures every other test class in this project happens to use so far):
//
//   1. Is SubjectScope actually enforced by the execution engine, or just a label in a doc?
//   2. Does the session genuinely cross from one WORKFLOW into a second one, not just between
//      templates within a single workflow (which is all prior tests ever exercised)?
@SpringBootTest
@Transactional
class CrossWorkflowFamilyHistoryIntegrationTest {

    @Autowired
    private WorkflowService workflowService;
    @Autowired
    private TemplateService templateService;
    @Autowired
    private QuestionService questionService;
    @Autowired
    private AssessmentService assessmentService;
    @Autowired
    private FamilyMemberService familyMemberService;
    @Autowired
    private ObjectMapper objectMapper;

    private UUID personalDetailsWorkflowId;
    private UUID familyHistoryWorkflowId;
    private UUID demographicsTemplateId;
    private UUID parentsTemplateId;
    private UUID firstNameQuestionId;
    private UUID knowsMotherQuestionId;

    @BeforeEach
    void seedTwoWorkflows() {
        // Workflow 1: Personal Details (displayOrder 1) -> DEMOGRAPHICS (BOTH)
        personalDetailsWorkflowId = createWorkflow(workflowService, "PERSONAL_DETAILS_XW", 1);
        demographicsTemplateId = createTemplate(templateService, personalDetailsWorkflowId, "DEMOGRAPHICS_XW", 1,
                null, SubjectScope.BOTH);
        firstNameQuestionId = createQuestion(questionService, demographicsTemplateId, "first_name", QuestionType.TEXT, 1,
                null, objectMapper.readTree("{\"required\":true}"), null);

        // Workflow 2: Family History (displayOrder 2) -> PARENTS (PROBAND_ONLY)
        familyHistoryWorkflowId = createWorkflow(workflowService, "FAMILY_HISTORY_XW", 2);
        parentsTemplateId = createTemplate(templateService, familyHistoryWorkflowId, "PARENTS_XW", 1,
                null, SubjectScope.PROBAND_ONLY);
        knowsMotherQuestionId = createQuestion(questionService, parentsTemplateId, "knows_biological_mother", QuestionType.RADIO, 1,
                objectMapper.readTree("[{\"label\":\"Yes\",\"value\":true},{\"label\":\"No\",\"value\":false}]"), null, null);
    }

    private UUID startSession() {
        return assessmentService.start(new AssessmentStartRequest(null)).sessionId();
    }

    // Verification request #1: the proband's own walk must genuinely leave workflow 1 and
    // land in workflow 2's template - a flat template list within one workflow would never
    // prove this, since "next template" could trivially stay inside the same workflow.
    @Test
    void probandCrossesFromPersonalDetailsWorkflowIntoFamilyHistoryWorkflow() {
        UUID sessionId = startSession();
        CurrentTemplateResponse start = assessmentService.currentTemplate(sessionId, null);
        assertThat(start.workflowId()).isEqualTo(personalDetailsWorkflowId);
        assertThat(start.templateCode()).isEqualTo("DEMOGRAPHICS_XW");

        assessmentService.submitAnswer(sessionId, new AnswerSubmitRequest(firstNameQuestionId,
                objectMapper.readTree("\"Safiya\""), null));

        CurrentTemplateResponse next = assessmentService.nextTemplate(sessionId, null);
        assertThat(next.completed()).isFalse();
        assertThat(next.workflowId()).isEqualTo(familyHistoryWorkflowId);
        assertThat(next.templateCode()).isEqualTo("PARENTS_XW");
    }

    // Verification request #2: SubjectScope actually drives execution, not just documentation.
    // A family member's pass must (a) land on DEMOGRAPHICS - reusing the SAME row in workflow 1,
    // never a duplicate - even though it's added while the proband is sitting in workflow 2, and
    // (b) never be offered PARENTS at all, since it's PROBAND_ONLY.
    @Test
    void familyMemberPassReusesDemographicsAcrossTheWorkflowBoundaryAndSkipsProbandOnlyFamilyHistory() {
        UUID sessionId = startSession();
        assessmentService.submitAnswer(sessionId, new AnswerSubmitRequest(firstNameQuestionId,
                objectMapper.readTree("\"Safiya\""), null));
        CurrentTemplateResponse probandPosition = assessmentService.nextTemplate(sessionId, null);
        assertThat(probandPosition.templateCode()).isEqualTo("PARENTS_XW"); // proband now deep in workflow 2

        FamilyMemberResponse mother = familyMemberService.create(sessionId,
                new FamilyMemberCreateRequest(RelationshipType.MOTHER, MaternalPaternalSideType.MATERNAL));
        UUID motherId = mother.id();
        CurrentTemplateResponse familyMemberPass = assessmentService.currentTemplate(sessionId, motherId);

        // Lands on DEMOGRAPHICS (workflow 1's template, id-for-id the same row), not PARENTS.
        assertThat(familyMemberPass.templateId()).isEqualTo(demographicsTemplateId);
        assertThat(familyMemberPass.workflowId()).isEqualTo(personalDetailsWorkflowId);
        assertThat(familyMemberPass.templateCode()).isEqualTo("DEMOGRAPHICS_XW");

        assessmentService.submitAnswer(sessionId, new AnswerSubmitRequest(firstNameQuestionId,
                objectMapper.readTree("\"Jane\""), motherId));

        // Her pass is immediately exhausted after the one BOTH-scope template - PARENTS_XW
        // (PROBAND_ONLY) must never be offered to her - a subject-scoped "done", not a transition
        // back to the proband (there's no "return" anymore; her pass and the proband's walk were
        // never sharing one position to begin with).
        CurrentTemplateResponse herWalkDone = assessmentService.nextTemplate(sessionId, motherId);
        assertThat(herWalkDone.completed()).isTrue();
        assertThat(herWalkDone.familyMemberId()).isEqualTo(motherId);

        // The proband's own position, independent of her pass throughout, is still exactly where
        // it was.
        CurrentTemplateResponse probandStillThere = assessmentService.currentTemplate(sessionId, null);
        assertThat(probandStillThere.templateCode()).isEqualTo("PARENTS_XW");
        assertThat(probandStillThere.workflowId()).isEqualTo(familyHistoryWorkflowId);
    }

    // A second, independent proof of SubjectScope enforcement: even a direct attempt to answer
    // the PROBAND_ONLY question while on her pass is rejected - not merely "never offered in a
    // list", but actively refused by the same template-legitimacy guard from Step 6.
    @Test
    void familyMemberCannotAnswerAProbandOnlyFamilyHistoryQuestionEvenIfAttemptedDirectly() {
        UUID sessionId = startSession();
        FamilyMemberResponse mother = familyMemberService.create(sessionId,
                new FamilyMemberCreateRequest(RelationshipType.MOTHER, MaternalPaternalSideType.MATERNAL));
        UUID motherId = mother.id();
        assessmentService.currentTemplate(sessionId, motherId); // resolves her entry position (DEMOGRAPHICS_XW)

        assertThatThrownBy(() -> assessmentService.submitAnswer(sessionId,
                new AnswerSubmitRequest(knowsMotherQuestionId, objectMapper.readTree("true"), motherId)))
                .isInstanceOf(InvalidAnswerException.class)
                .hasMessageContaining("DEMOGRAPHICS_XW");
    }
}
