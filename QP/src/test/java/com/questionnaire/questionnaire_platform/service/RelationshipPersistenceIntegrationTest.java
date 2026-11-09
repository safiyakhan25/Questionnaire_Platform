package com.questionnaire.questionnaire_platform.service;

import com.questionnaire.questionnaire_platform.dto.AssessmentStartRequest;
import com.questionnaire.questionnaire_platform.dto.FamilyMemberCreateRequest;
import com.questionnaire.questionnaire_platform.dto.FamilyMemberResponse;
import com.questionnaire.questionnaire_platform.dto.ParentChildRelationshipCreateRequest;
import com.questionnaire.questionnaire_platform.dto.ParentChildRelationshipResponse;
import com.questionnaire.questionnaire_platform.dto.PartnerRelationshipCreateRequest;
import com.questionnaire.questionnaire_platform.dto.PartnerRelationshipResponse;
import com.questionnaire.questionnaire_platform.entity.MatingLineType;
import com.questionnaire.questionnaire_platform.entity.MaternalPaternalSideType;
import com.questionnaire.questionnaire_platform.entity.QuestionType;
import com.questionnaire.questionnaire_platform.entity.RelationshipStatusType;
import com.questionnaire.questionnaire_platform.entity.ReproductiveStatusType;
import com.questionnaire.questionnaire_platform.entity.RelationshipType;
import com.questionnaire.questionnaire_platform.entity.SubjectScope;
import com.questionnaire.questionnaire_platform.exception.InvalidRelationshipException;
import com.questionnaire.questionnaire_platform.exception.ResourceInUseException;
import com.questionnaire.questionnaire_platform.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static com.questionnaire.questionnaire_platform.support.TestFixtures.createQuestion;
import static com.questionnaire.questionnaire_platform.support.TestFixtures.createTemplate;
import static com.questionnaire.questionnaire_platform.support.TestFixtures.createWorkflow;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Covers the relationship-persistence surface (PARENT_CHILD_RELATIONSHIP/PARTNER_RELATIONSHIP)
// that had zero automated coverage before this audit pass, despite being a fully built feature.
@SpringBootTest
@Transactional
class RelationshipPersistenceIntegrationTest {

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
    private ParentChildRelationshipService parentChildRelationshipService;
    @Autowired
    private PartnerRelationshipService partnerRelationshipService;

    private UUID sessionId;
    private UUID probandId;
    private UUID motherId;
    private UUID childId;
    private UUID firstNameQuestionId;

    @BeforeEach
    void seedWorkflowAndFamily() {
        UUID workflowId = createWorkflow(workflowService, "TEST_WF", 1);
        UUID templateId = createTemplate(templateService, workflowId, "BASIC", 1, null, SubjectScope.BOTH);
        firstNameQuestionId = createQuestion(questionService, templateId, "first_name", QuestionType.TEXT, 1, null, null, null);

        sessionId = assessmentService.start(new AssessmentStartRequest(null)).sessionId();
        motherId = familyMemberService.create(sessionId,
                new FamilyMemberCreateRequest(RelationshipType.MOTHER, MaternalPaternalSideType.MATERNAL)).id();
        assessmentService.nextTemplate(sessionId, motherId); // exhaust her pass, nothing required
        childId = familyMemberService.create(sessionId,
                new FamilyMemberCreateRequest(RelationshipType.DAUGHTER, MaternalPaternalSideType.UNKNOWN)).id();
        assessmentService.nextTemplate(sessionId, childId);

        probandId = familyMemberService.getAll(sessionId).get(0).probandId();
    }

    @Test
    void parentChildRelationshipPersistsWithCorrectXorOwnership() {
        ParentChildRelationshipResponse response = parentChildRelationshipService.create(sessionId,
                new ParentChildRelationshipCreateRequest(motherId, childId));

        assertThat(response.parentFamilyMemberId()).isEqualTo(motherId);
        assertThat(response.parentPersonId()).isNull();
        assertThat(response.childFamilyMemberId()).isEqualTo(childId);

        // 2, not 1: creating childId as DAUGHTER (scope defaults to the proband) in @BeforeEach
        // already auto-created a proband->child edge (QP-Family-Relationship-Persistence-Design.md)
        // - this explicit mother->child edge is a second, independent one.
        List<ParentChildRelationshipResponse> all = parentChildRelationshipService.getAll(sessionId);
        assertThat(all).hasSize(2).contains(response);
    }

    @Test
    void probandAsParentUsesThePersonSideOfTheXor() {
        ParentChildRelationshipResponse response = parentChildRelationshipService.create(sessionId,
                new ParentChildRelationshipCreateRequest(probandId, childId));

        assertThat(response.parentPersonId()).isEqualTo(probandId);
        assertThat(response.parentFamilyMemberId()).isNull();
    }

    @Test
    void probandCannotBeTheChild() {
        assertThatThrownBy(() -> parentChildRelationshipService.create(sessionId,
                new ParentChildRelationshipCreateRequest(motherId, probandId)))
                .isInstanceOf(InvalidRelationshipException.class);
    }

    @Test
    void parentAndChildCannotBeTheSameIndividual() {
        assertThatThrownBy(() -> parentChildRelationshipService.create(sessionId,
                new ParentChildRelationshipCreateRequest(childId, childId)))
                .isInstanceOf(InvalidRelationshipException.class);
    }

    @Test
    void deletingAParentChildRelationshipSucceeds() {
        ParentChildRelationshipResponse response = parentChildRelationshipService.create(sessionId,
                new ParentChildRelationshipCreateRequest(motherId, childId));

        parentChildRelationshipService.delete(sessionId, response.id());

        // Not empty: the proband->child edge auto-created when childId was added as DAUGHTER in
        // @BeforeEach is untouched - only the explicitly-created mother->child edge is gone.
        assertThat(parentChildRelationshipService.getAll(sessionId))
                .extracting(ParentChildRelationshipResponse::id)
                .doesNotContain(response.id());
    }

    @Test
    void partnerRelationshipDefaultsApplyWhenOmitted() {
        PartnerRelationshipResponse response = partnerRelationshipService.create(sessionId,
                new PartnerRelationshipCreateRequest(probandId, motherId, null, null, null, null, null, null, null));

        assertThat(response.matingLineType()).isEqualTo(MatingLineType.MATING_LINE);
        assertThat(response.reproductiveStatus()).isEqualTo(ReproductiveStatusType.UNKNOWN);
    }

    @Test
    void unshownChildrenCountIsRequiredForThatReproductiveStatus() {
        assertThatThrownBy(() -> partnerRelationshipService.create(sessionId,
                new PartnerRelationshipCreateRequest(probandId, motherId, null,
                        ReproductiveStatusType.HAS_CHILDREN_NOT_SHOWN, null, null, null, null, null)))
                .isInstanceOf(InvalidRelationshipException.class);

        PartnerRelationshipResponse response = partnerRelationshipService.create(sessionId,
                new PartnerRelationshipCreateRequest(probandId, motherId, null,
                        ReproductiveStatusType.HAS_CHILDREN_NOT_SHOWN, 2, null, null, null, null));
        assertThat(response.unshownChildrenCount()).isEqualTo(2);
    }

    // consanguinityDegree's required-when condition is keyed off consanguineous, not matingLineType
    // anymore (mentor review 2026-10-14: a couple can be married AND biologically related at once -
    // one field can't represent both). The two assertions below prove the decoupling both ways.
    @Test
    void consanguinityDegreeIsRequiredWhenConsanguineousIsTrue() {
        assertThatThrownBy(() -> partnerRelationshipService.create(sessionId,
                new PartnerRelationshipCreateRequest(probandId, motherId, null,
                        null, null, null, true, null, null)))
                .isInstanceOf(InvalidRelationshipException.class);
    }

    @Test
    void matingLineTypeConsanguineousNoLongerForcesConsanguinityDegreeByItself() {
        // matingLineType is untouched, still its own field - setting it to CONSANGUINEOUS alone
        // (consanguineous left null/false) must NOT trigger the consanguinityDegree requirement.
        PartnerRelationshipResponse response = partnerRelationshipService.create(sessionId,
                new PartnerRelationshipCreateRequest(probandId, motherId, MatingLineType.CONSANGUINEOUS,
                        null, null, null, null, null, null));
        assertThat(response.matingLineType()).isEqualTo(MatingLineType.CONSANGUINEOUS);
        assertThat(response.consanguinityDegree()).isNull();
    }

    @Test
    void relationshipStatusAndConsanguineousAreIndependentlyCapturedAndPersisted() {
        PartnerRelationshipResponse response = partnerRelationshipService.create(sessionId,
                new PartnerRelationshipCreateRequest(probandId, motherId, null, null, null,
                        RelationshipStatusType.MARRIED, true, "First cousins", null));

        assertThat(response.relationshipStatus()).isEqualTo(RelationshipStatusType.MARRIED);
        assertThat(response.consanguineous()).isTrue();
        assertThat(response.consanguinityDegree()).isEqualTo("First cousins");
        // matingLineType stays at its own untouched default - nothing here derives or overrides it.
        assertThat(response.matingLineType()).isEqualTo(MatingLineType.MATING_LINE);
    }

    @Test
    void endedYearIsOnlyValidForEndedMatingLineTypes() {
        assertThatThrownBy(() -> partnerRelationshipService.create(sessionId,
                new PartnerRelationshipCreateRequest(probandId, motherId, MatingLineType.MATING_LINE,
                        null, null, null, null, null, 2020)))
                .isInstanceOf(InvalidRelationshipException.class);

        PartnerRelationshipResponse response = partnerRelationshipService.create(sessionId,
                new PartnerRelationshipCreateRequest(probandId, motherId, MatingLineType.DIVORCED,
                        null, null, null, null, null, 2020));
        assertThat(response.endedYear()).isEqualTo(2020);
    }

    // The question set never submits matingLineType directly anymore (it stays at its untouched
    // default) - endedYear must still be acceptable when gated purely through relationshipStatus,
    // the field the questionnaire actually asks.
    @Test
    void endedYearIsAlsoValidWhenGatedThroughRelationshipStatusAlone() {
        assertThatThrownBy(() -> partnerRelationshipService.create(sessionId,
                new PartnerRelationshipCreateRequest(probandId, motherId, null, null, null,
                        RelationshipStatusType.MARRIED, null, null, 2020)))
                .isInstanceOf(InvalidRelationshipException.class);

        PartnerRelationshipResponse response = partnerRelationshipService.create(sessionId,
                new PartnerRelationshipCreateRequest(probandId, motherId, null, null, null,
                        RelationshipStatusType.SEPARATED, null, null, 2021));
        assertThat(response.endedYear()).isEqualTo(2021);
        assertThat(response.matingLineType()).isEqualTo(MatingLineType.MATING_LINE);
    }

    @Test
    void theTwoPartnersCannotBeTheSameIndividual() {
        assertThatThrownBy(() -> partnerRelationshipService.create(sessionId,
                new PartnerRelationshipCreateRequest(motherId, motherId, null, null, null, null, null, null, null)))
                .isInstanceOf(InvalidRelationshipException.class);
    }

    @Test
    void anIndividualFromAnotherSessionIsRejected() {
        UUID otherSessionId = assessmentService.start(new AssessmentStartRequest(null)).sessionId();

        assertThatThrownBy(() -> parentChildRelationshipService.create(otherSessionId,
                new ParentChildRelationshipCreateRequest(motherId, childId)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deletingARelationshipOnACompletedSessionIsRejected() {
        ParentChildRelationshipResponse response = parentChildRelationshipService.create(sessionId,
                new ParentChildRelationshipCreateRequest(motherId, childId));

        // Nothing left for the proband's own walk in this fixture - one answer advances it to completion.
        assessmentService.submitAnswer(sessionId, new com.questionnaire.questionnaire_platform.dto.AnswerSubmitRequest(
                firstNameQuestionId, new tools.jackson.databind.ObjectMapper().readTree("\"Safiya\""), null));
        assessmentService.nextTemplate(sessionId, null);

        assertThatThrownBy(() -> parentChildRelationshipService.delete(sessionId, response.id()))
                .isInstanceOf(ResourceInUseException.class);
    }

    @Test
    void familyMemberDeletionIsBlockedByAPartnerRelationshipToo() {
        partnerRelationshipService.create(sessionId,
                new PartnerRelationshipCreateRequest(probandId, motherId, null, null, null, null, null, null, null));

        assertThatThrownBy(() -> familyMemberService.delete(sessionId, motherId))
                .isInstanceOf(ResourceInUseException.class);
    }
}
