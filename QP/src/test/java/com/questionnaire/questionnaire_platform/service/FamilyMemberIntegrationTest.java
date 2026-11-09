package com.questionnaire.questionnaire_platform.service;

import com.questionnaire.questionnaire_platform.dto.AnswerSubmitRequest;
import com.questionnaire.questionnaire_platform.dto.AssessmentStartRequest;
import com.questionnaire.questionnaire_platform.dto.CurrentTemplateResponse;
import com.questionnaire.questionnaire_platform.dto.FamilyMemberCreateRequest;
import com.questionnaire.questionnaire_platform.dto.FamilyMemberResponse;
import com.questionnaire.questionnaire_platform.dto.RelativeCountReconciliation;
import com.questionnaire.questionnaire_platform.entity.MaternalPaternalSideType;
import com.questionnaire.questionnaire_platform.entity.QuestionType;
import com.questionnaire.questionnaire_platform.entity.RelationshipType;
import com.questionnaire.questionnaire_platform.entity.SubjectScope;
import com.questionnaire.questionnaire_platform.exception.DuplicateResourceException;
import com.questionnaire.questionnaire_platform.exception.ResourceInUseException;
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

// Covers: family-member creation (and pass start), deletion, duplicate prevention (FR-13.1),
// and relationship_instance_id isolation (answers staying separate between subjects).
@SpringBootTest
@Transactional
class FamilyMemberIntegrationTest {

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

    private UUID firstNameQuestionId;
    private UUID basicTemplateId;

    @BeforeEach
    void seedWorkflow() {
        UUID workflowId = createWorkflow(workflowService, "TEST_WF", 1);
        basicTemplateId = createTemplate(templateService, workflowId, "BASIC", 1, null, SubjectScope.BOTH);
        firstNameQuestionId = createQuestion(questionService, basicTemplateId, "first_name", QuestionType.TEXT, 1,
                null, null, null);
    }

    private UUID startSession() {
        return assessmentService.start(new AssessmentStartRequest(null)).sessionId();
    }

    @Test
    void addingAFamilyMemberDoesNotStartTheirPassButContinuingDoes() {
        UUID sessionId = startSession();
        FamilyMemberResponse mother = familyMemberService.create(sessionId,
                new FamilyMemberCreateRequest(RelationshipType.MOTHER, MaternalPaternalSideType.MATERNAL));
        assertThat(mother.id()).isNotNull();

        // Continuing her pass (the equivalent of today's "Continue" click) resolves her entry
        // template the first time it's asked for - add no longer auto-starts it.
        CurrentTemplateResponse pass = assessmentService.currentTemplate(sessionId, mother.id());
        assertThat(pass.templateCode()).isEqualTo("BASIC");
        assertThat(pass.familyMemberId()).isEqualTo(mother.id());
    }

    @Test
    void secondMotherIsRejectedButSecondGrandmotherIsAllowed() {
        UUID sessionId = startSession();
        FamilyMemberResponse mother = familyMemberService.create(sessionId,
                new FamilyMemberCreateRequest(RelationshipType.MOTHER, MaternalPaternalSideType.MATERNAL));
        assessmentService.nextTemplate(sessionId, mother.id()); // exhaust her pass (nothing required, no gating question)

        assertThatThrownBy(() -> familyMemberService.create(sessionId,
                new FamilyMemberCreateRequest(RelationshipType.MOTHER, MaternalPaternalSideType.MATERNAL)))
                .isInstanceOf(DuplicateResourceException.class);

        FamilyMemberResponse maternalGrandmother = familyMemberService.create(sessionId,
                new FamilyMemberCreateRequest(RelationshipType.GRANDMOTHER, MaternalPaternalSideType.MATERNAL));
        assessmentService.nextTemplate(sessionId, maternalGrandmother.id());
        familyMemberService.create(sessionId,
                new FamilyMemberCreateRequest(RelationshipType.GRANDMOTHER, MaternalPaternalSideType.PATERNAL));

        List<FamilyMemberResponse> familyMembers = familyMemberService.getAll(sessionId);
        assertThat(familyMembers).extracting(fm -> fm.roles().get(0).relationshipType())
                .containsExactly(RelationshipType.MOTHER, RelationshipType.GRANDMOTHER, RelationshipType.GRANDMOTHER);
    }

    @Test
    void familyMemberAnswersStayIsolatedFromTheProbandAndEachOther() {
        UUID sessionId = startSession();
        assessmentService.submitAnswer(sessionId, new AnswerSubmitRequest(
                firstNameQuestionId, objectMapper.readTree("\"ProbandName\""), null));
        // Deliberately not calling next-template here - BASIC is the proband's only template, so
        // doing so would complete the session before the family member can legitimately be added
        // (Step 6 correctly rejects adding a family member to a COMPLETED session).

        FamilyMemberResponse mother = familyMemberService.create(sessionId,
                new FamilyMemberCreateRequest(RelationshipType.MOTHER, MaternalPaternalSideType.MATERNAL));
        assessmentService.submitAnswer(sessionId, new AnswerSubmitRequest(
                firstNameQuestionId, objectMapper.readTree("\"MotherName\""), mother.id()));
        assessmentService.nextTemplate(sessionId, mother.id());

        FamilyMemberResponse father = familyMemberService.create(sessionId,
                new FamilyMemberCreateRequest(RelationshipType.FATHER, MaternalPaternalSideType.PATERNAL));
        assessmentService.submitAnswer(sessionId, new AnswerSubmitRequest(
                firstNameQuestionId, objectMapper.readTree("\"FatherName\""), father.id()));

        FamilyMemberResponse motherReloaded = familyMemberService.getById(sessionId, mother.id());
        FamilyMemberResponse fatherReloaded = familyMemberService.getById(sessionId, father.id());
        assertThat(motherReloaded.firstName()).isEqualTo("MotherName");
        assertThat(fatherReloaded.firstName()).isEqualTo("FatherName");
    }

    @Test
    void deletingAFamilyMemberWithRecordedAnswersIsBlocked() {
        UUID sessionId = startSession();
        FamilyMemberResponse mother = familyMemberService.create(sessionId,
                new FamilyMemberCreateRequest(RelationshipType.MOTHER, MaternalPaternalSideType.MATERNAL));
        assessmentService.submitAnswer(sessionId, new AnswerSubmitRequest(
                firstNameQuestionId, objectMapper.readTree("\"MotherName\""), mother.id()));
        assessmentService.nextTemplate(sessionId, mother.id());

        assertThatThrownBy(() -> familyMemberService.delete(sessionId, mother.id()))
                .isInstanceOf(ResourceInUseException.class);
    }

    @Test
    void deletingAFamilyMemberWithNoAnswersSucceeds() {
        UUID sessionId = startSession();
        FamilyMemberResponse mother = familyMemberService.create(sessionId,
                new FamilyMemberCreateRequest(RelationshipType.MOTHER, MaternalPaternalSideType.MATERNAL));
        assessmentService.nextTemplate(sessionId, mother.id()); // exhaust pass without answering anything (not required)

        familyMemberService.delete(sessionId, mother.id());

        assertThat(familyMemberService.getAll(sessionId)).isEmpty();
    }

    @Test
    void reconciliationReportsAMismatchThenMatchesOnceCorrected() {
        UUID siblingCountQuestionId = createQuestion(questionService, basicTemplateId, "sibling_count",
                QuestionType.NUMBER, 2, null, null, null);
        UUID sessionId = startSession();

        // No siblings declared yet, none added - every group should report no mismatch.
        List<RelativeCountReconciliation> initial = familyMemberService.reconcileRelativeCounts(sessionId);
        assertThat(initial).allSatisfy(row -> assertThat(row.matches()).isTrue());

        FamilyMemberResponse brother = familyMemberService.create(sessionId,
                new FamilyMemberCreateRequest(RelationshipType.BROTHER, MaternalPaternalSideType.UNKNOWN));
        assessmentService.nextTemplate(sessionId, brother.id()); // exhaust his pass

        assessmentService.submitAnswer(sessionId,
                new AnswerSubmitRequest(siblingCountQuestionId, objectMapper.readTree("2"), null));

        RelativeCountReconciliation siblingRow = familyMemberService.reconcileRelativeCounts(sessionId).stream()
                .filter(r -> r.declaredCountFactKey().equals("sibling_count")).findFirst().orElseThrow();
        assertThat(siblingRow.declaredCount()).isEqualTo(2);
        assertThat(siblingRow.actualCount()).isEqualTo(1);
        assertThat(siblingRow.matches()).isFalse();

        assessmentService.submitAnswer(sessionId,
                new AnswerSubmitRequest(siblingCountQuestionId, objectMapper.readTree("1"), null));

        RelativeCountReconciliation corrected = familyMemberService.reconcileRelativeCounts(sessionId).stream()
                .filter(r -> r.declaredCountFactKey().equals("sibling_count")).findFirst().orElseThrow();
        assertThat(corrected.matches()).isTrue();
    }
}
