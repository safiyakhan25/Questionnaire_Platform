package com.questionnaire.questionnaire_platform.service;

import com.questionnaire.questionnaire_platform.dto.AnswerSubmitRequest;
import com.questionnaire.questionnaire_platform.dto.AssessmentStartRequest;
import com.questionnaire.questionnaire_platform.dto.CurrentTemplateResponse;
import com.questionnaire.questionnaire_platform.dto.FamilyMemberCreateRequest;
import com.questionnaire.questionnaire_platform.dto.FamilyMemberResponse;
import com.questionnaire.questionnaire_platform.dto.QuestionAnswerView;
import com.questionnaire.questionnaire_platform.dto.QuestionResponseView;
import com.questionnaire.questionnaire_platform.dto.SessionSummaryResponse;
import com.questionnaire.questionnaire_platform.entity.MaternalPaternalSideType;
import com.questionnaire.questionnaire_platform.entity.QuestionType;
import com.questionnaire.questionnaire_platform.entity.RelationshipType;
import com.questionnaire.questionnaire_platform.entity.SubjectScope;
import com.questionnaire.questionnaire_platform.exception.InvalidAnswerException;
import com.questionnaire.questionnaire_platform.exception.ResourceNotFoundException;
import com.questionnaire.questionnaire_platform.exception.TemplateIncompleteException;
import com.questionnaire.questionnaire_platform.repository.PersonRepository;
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

// Covers: question visibility and template navigation, required-question validation (FR-6),
// answer validation and persistence, session completion and resume. Rolled back after each test -
// nothing here leaks into questionnaire_platform_test between runs.
@SpringBootTest
@Transactional
class AssessmentFlowIntegrationTest {

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
    private PersonRepository personRepository;
    @Autowired
    private ObjectMapper objectMapper;

    private UUID workflowId;
    private UUID firstNameQuestionId;
    private UUID genderQuestionId;
    private UUID isPregnantQuestionId;
    private UUID familyMemberFirstNameQuestionId;

    @BeforeEach
    void seedWorkflow() {
        workflowId = createWorkflow(workflowService, "TEST_WF", 1);
        UUID basicTemplateId = createTemplate(templateService, workflowId, "BASIC", 1, null, SubjectScope.PROBAND_ONLY);
        firstNameQuestionId = createQuestion(questionService, basicTemplateId, "first_name", QuestionType.TEXT, 1,
                null, objectMapper.readTree("{\"required\":true}"), null);
        genderQuestionId = createQuestion(questionService, basicTemplateId, "gender_at_birth", QuestionType.RADIO, 2,
                objectMapper.readTree("[{\"label\":\"Male\",\"value\":\"MALE\"},{\"label\":\"Female\",\"value\":\"FEMALE\"}]"),
                objectMapper.readTree("{\"required\":true}"), null);

        UUID womenTemplateId = createTemplate(templateService, workflowId, "WOMEN", 2,
                objectMapper.readTree("{\"==\":[{\"var\":\"gender_at_birth\"},\"FEMALE\"]}"), SubjectScope.PROBAND_ONLY);
        isPregnantQuestionId = createQuestion(questionService, womenTemplateId, "is_pregnant", QuestionType.RADIO, 1,
                objectMapper.readTree("[{\"label\":\"Yes\",\"value\":true},{\"label\":\"No\",\"value\":false}]"),
                objectMapper.readTree("{\"required\":true}"), null);

        // FAMILY_MEMBER_ONLY - never appears in the proband's own walk (BASIC -> WOMEN -> complete,
        // unaffected), but is the only template a family member's pass ever walks in this fixture.
        UUID familyInfoTemplateId = createTemplate(templateService, workflowId, "FAMILY_INFO", 3, null, SubjectScope.FAMILY_MEMBER_ONLY);
        familyMemberFirstNameQuestionId = createQuestion(questionService, familyInfoTemplateId, "family_first_name", QuestionType.TEXT, 1,
                null, null, null);
    }

    private UUID startSession() {
        return assessmentService.start(new AssessmentStartRequest(null)).sessionId();
    }

    private void answer(UUID sessionId, UUID questionId, String jsonValue) {
        assessmentService.submitAnswer(sessionId, new AnswerSubmitRequest(questionId, objectMapper.readTree(jsonValue), null));
    }

    private void answerAsFamilyMember(UUID sessionId, UUID familyMemberId, UUID questionId, String jsonValue) {
        assessmentService.submitAnswer(sessionId, new AnswerSubmitRequest(questionId, objectMapper.readTree(jsonValue), familyMemberId));
    }

    @Test
    void templateNavigationSkipsInapplicableTemplate() {
        UUID sessionId = startSession();
        answer(sessionId, firstNameQuestionId, "\"Safiya\"");
        answer(sessionId, genderQuestionId, "\"MALE\"");

        CurrentTemplateResponse next = assessmentService.nextTemplate(sessionId, null);
        assertThat(next.completed()).isTrue();
    }

    @Test
    void templateNavigationEntersApplicableConditionalTemplate() {
        UUID sessionId = startSession();
        answer(sessionId, firstNameQuestionId, "\"Safiya\"");
        answer(sessionId, genderQuestionId, "\"FEMALE\"");

        CurrentTemplateResponse next = assessmentService.nextTemplate(sessionId, null);
        assertThat(next.completed()).isFalse();
        assertThat(next.templateCode()).isEqualTo("WOMEN");
    }

    // Step 6: the exact gap found by accident earlier in this project (a test script submitted an
    // answer for a not-yet-reached template and it silently succeeded) - now explicitly covered.
    @Test
    void answeringAQuestionFromATemplateNotYetReachedIsRejected() {
        UUID sessionId = startSession();
        // Session is on BASIC - isPregnantQuestionId belongs to WOMEN, never reached yet.
        assertThatThrownBy(() -> answer(sessionId, isPregnantQuestionId, "false"))
                .isInstanceOf(InvalidAnswerException.class)
                .hasMessageContaining("WOMEN");

        assertThat(assessmentService.responses(sessionId)).isEmpty();
    }

    @Test
    void requiredQuestionBlocksTemplateCompletion() {
        UUID sessionId = startSession();
        answer(sessionId, firstNameQuestionId, "\"Safiya\"");
        // gender_at_birth left unanswered - BASIC still has a required, visible, unanswered question

        assertThatThrownBy(() -> assessmentService.nextTemplate(sessionId, null))
                .isInstanceOf(TemplateIncompleteException.class)
                .hasMessageContaining("gender_at_birth");
    }

    @Test
    void answerIsValidatedBeforePersistence() {
        UUID sessionId = startSession();
        assertThatThrownBy(() -> answer(sessionId, genderQuestionId, "\"NOT_AN_OPTION\""))
                .isInstanceOf(InvalidAnswerException.class);

        List<QuestionResponseView> responses = assessmentService.responses(sessionId);
        assertThat(responses).isEmpty();
    }

    @Test
    void answerPersistsAndUpdatesTheBusinessEntity() {
        UUID sessionId = startSession();
        QuestionResponseView response = assessmentService.submitAnswer(sessionId,
                new AnswerSubmitRequest(firstNameQuestionId, objectMapper.readTree("\"Safiya\""), null));

        assertThat(response.answerValue().asString()).isEqualTo("Safiya");

        CurrentTemplateResponse current = assessmentService.currentTemplate(sessionId, null);
        QuestionAnswerView firstNameView = current.questions().stream()
                .filter(q -> q.id().equals(firstNameQuestionId)).findFirst().orElseThrow();
        assertThat(firstNameView.value().asString()).isEqualTo("Safiya");
    }

    @Test
    void sessionCompletesAndResumeRulesAreEnforced() {
        UUID sessionId = startSession();
        answer(sessionId, firstNameQuestionId, "\"Safiya\"");
        answer(sessionId, genderQuestionId, "\"MALE\"");
        assessmentService.nextTemplate(sessionId, null);

        assertThat(assessmentService.status(sessionId).status().name()).isEqualTo("COMPLETED");

        // A completed session cannot be resumed via /start - rejected, not silently restarted.
        assertThatThrownBy(() -> assessmentService.start(new AssessmentStartRequest(sessionId)))
                .isInstanceOf(ResourceNotFoundException.class);

        // Nor can it still be written to.
        assertThatThrownBy(() -> answer(sessionId, firstNameQuestionId, "\"Changed\""))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void resumingAnInProgressSessionDoesNotCreateADuplicatePerson() {
        long before = personRepository.count();
        UUID sessionId = startSession();
        assertThat(personRepository.count()).isEqualTo(before + 1);

        assessmentService.start(new AssessmentStartRequest(sessionId));
        assertThat(personRepository.count()).isEqualTo(before + 1);
    }

    @Test
    void questionVisibilityReflectsCurrentFacts() {
        UUID sessionId = startSession();
        answer(sessionId, firstNameQuestionId, "\"Safiya\"");
        answer(sessionId, genderQuestionId, "\"FEMALE\"");
        assessmentService.nextTemplate(sessionId, null); // now on WOMEN

        CurrentTemplateResponse current = assessmentService.currentTemplate(sessionId, null);
        assertThat(current.templateCode()).isEqualTo("WOMEN");
        QuestionAnswerView isPregnantView = current.questions().stream()
                .filter(q -> q.id().equals(isPregnantQuestionId)).findFirst().orElseThrow();
        assertThat(isPregnantView.visible()).isTrue();
    }

    // The actual promise of the independent-per-subject-progress change: the proband and a family
    // member's positions never clobber each other, no matter how many times the proband switches
    // between them. Critically, this must hold MID-PASS, not just at entry/done - a family member
    // advanced partway through their own multi-template walk must be restored exactly there, never
    // reset to their entry template, when the proband switches away and back (final-review finding,
    // 2026-10-30: the original version of this test only ever had a one-template family-member walk,
    // so it couldn't actually distinguish "restored mid-pass" from "reset to entry").
    @Test
    void probandAndFamilyMemberPositionsAreIndependentAcrossInterleavedSwitches() {
        // A second FAMILY_MEMBER_ONLY template, local to this test only, so her pass has somewhere
        // to advance TO before finishing - proving restoration mid-pass, not just entry-vs-done.
        UUID familyInfo2TemplateId = createTemplate(templateService, workflowId, "FAMILY_INFO_2", 4,
                null, SubjectScope.FAMILY_MEMBER_ONLY);
        UUID familyMemberLastNameQuestionId = createQuestion(questionService, familyInfo2TemplateId, "family_last_name",
                QuestionType.TEXT, 1, null, null, null);

        UUID sessionId = startSession();
        answer(sessionId, firstNameQuestionId, "\"Safiya\"");
        answer(sessionId, genderQuestionId, "\"MALE\"");
        // Deliberately not calling nextTemplate yet - a COMPLETED session rejects adding a family
        // member (requireInProgress), so family members are added while the proband is still
        // mid-walk, same as every other family-member test in this project.

        FamilyMemberResponse mother = familyMemberService.create(sessionId,
                new FamilyMemberCreateRequest(RelationshipType.MOTHER, MaternalPaternalSideType.MATERNAL));
        CurrentTemplateResponse motherEntry = assessmentService.currentTemplate(sessionId, mother.id());
        assertThat(motherEntry.templateCode()).isEqualTo("FAMILY_INFO");
        answerAsFamilyMember(sessionId, mother.id(), familyMemberFirstNameQuestionId, "\"Jane\"");
        CurrentTemplateResponse motherAdvanced = assessmentService.nextTemplate(sessionId, mother.id());
        assertThat(motherAdvanced.completed()).isFalse(); // one more template left - not done yet
        assertThat(motherAdvanced.templateCode()).isEqualTo("FAMILY_INFO_2");

        // switching back to the proband - still exactly on BASIC, untouched by her pass
        CurrentTemplateResponse probandAfterMother = assessmentService.currentTemplate(sessionId, null);
        assertThat(probandAfterMother.completed()).isFalse();
        assertThat(probandAfterMother.templateCode()).isEqualTo("BASIC");

        // a second family member, added while the proband is still mid-walk, gets her own fresh entry
        FamilyMemberResponse father = familyMemberService.create(sessionId,
                new FamilyMemberCreateRequest(RelationshipType.FATHER, MaternalPaternalSideType.PATERNAL));
        CurrentTemplateResponse fatherEntry = assessmentService.currentTemplate(sessionId, father.id());
        assertThat(fatherEntry.templateCode()).isEqualTo("FAMILY_INFO");
        assertThat(fatherEntry.completed()).isFalse();

        // switching back to the mother - mid-pass on FAMILY_INFO_2 - must be restored exactly there,
        // never reset to her entry template (FAMILY_INFO), despite the proband and father both
        // having been active in between.
        CurrentTemplateResponse motherAgain = assessmentService.currentTemplate(sessionId, mother.id());
        assertThat(motherAgain.completed()).isFalse();
        assertThat(motherAgain.templateCode()).isEqualTo("FAMILY_INFO_2");

        // finishing her pass from exactly where it was restored
        answerAsFamilyMember(sessionId, mother.id(), familyMemberLastNameQuestionId, "\"Doe\"");
        CurrentTemplateResponse motherDone = assessmentService.nextTemplate(sessionId, mother.id());
        assertThat(motherDone.completed()).isTrue();
        assertThat(motherDone.familyMemberId()).isEqualTo(mother.id());

        // and finally, the proband's own walk - untouched by either pass the entire time - still
        // advances normally from exactly where it was left
        CurrentTemplateResponse probandFinal = assessmentService.currentTemplate(sessionId, null);
        assertThat(probandFinal.templateCode()).isEqualTo("BASIC");
        CurrentTemplateResponse probandCompletion = assessmentService.nextTemplate(sessionId, null);
        assertThat(probandCompletion.completed()).isTrue();
    }

    @Test
    void listSessionsReturnsDisplayNameFromPersonOrPlaceholderWhenUnnamed() {
        UUID namedSessionId = startSession();
        answer(namedSessionId, firstNameQuestionId, "\"Safiya\"");
        UUID unnamedSessionId = startSession();

        List<SessionSummaryResponse> sessions = assessmentService.listSessions();

        assertThat(sessions).extracting(SessionSummaryResponse::sessionId)
                .contains(namedSessionId, unnamedSessionId);
        SessionSummaryResponse named = sessions.stream()
                .filter(s -> s.sessionId().equals(namedSessionId)).findFirst().orElseThrow();
        assertThat(named.displayName()).contains("Safiya");
        SessionSummaryResponse unnamed = sessions.stream()
                .filter(s -> s.sessionId().equals(unnamedSessionId)).findFirst().orElseThrow();
        assertThat(unnamed.displayName()).startsWith("Unnamed");
    }

    @Test
    void submittingAnAnswerForAFamilyMemberFromAnotherSessionIsRejected() {
        UUID sessionId = startSession();
        UUID otherSessionId = startSession();
        FamilyMemberResponse otherSessionsMother = familyMemberService.create(otherSessionId,
                new FamilyMemberCreateRequest(RelationshipType.MOTHER, MaternalPaternalSideType.MATERNAL));

        assertThatThrownBy(() -> answerAsFamilyMember(sessionId, otherSessionsMother.id(),
                familyMemberFirstNameQuestionId, "\"Spoofed\""))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
