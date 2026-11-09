package com.questionnaire.questionnaire_platform.service;

import com.questionnaire.questionnaire_platform.dto.AnswerSubmitRequest;
import com.questionnaire.questionnaire_platform.dto.AssessmentStartRequest;
import com.questionnaire.questionnaire_platform.dto.FamilyMemberCreateRequest;
import com.questionnaire.questionnaire_platform.entity.MaternalPaternalSideType;
import com.questionnaire.questionnaire_platform.entity.QuestionType;
import com.questionnaire.questionnaire_platform.entity.RelationshipType;
import com.questionnaire.questionnaire_platform.entity.SubjectScope;
import com.questionnaire.questionnaire_platform.exception.InvalidAnswerException;
import com.questionnaire.questionnaire_platform.repository.AdoptionParentRepository;
import com.questionnaire.questionnaire_platform.repository.AdoptionRepository;
import com.questionnaire.questionnaire_platform.repository.AssistedReproductionParticipantRepository;
import com.questionnaire.questionnaire_platform.repository.AssistedReproductionRepository;
import com.questionnaire.questionnaire_platform.repository.DiseaseRepository;
import com.questionnaire.questionnaire_platform.repository.GenderTransitionRepository;
import com.questionnaire.questionnaire_platform.repository.MutationRepository;
import com.questionnaire.questionnaire_platform.repository.PregnancyOutcomeRepository;
import com.questionnaire.questionnaire_platform.repository.QuestionnaireSessionRepository;
import com.questionnaire.questionnaire_platform.repository.TwinshipMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static com.questionnaire.questionnaire_platform.support.TestFixtures.createQuestion;
import static com.questionnaire.questionnaire_platform.support.TestFixtures.createTemplate;
import static com.questionnaire.questionnaire_platform.support.TestFixtures.createWorkflow;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Proves the STRUCTURED plumbing (QuestionType.STRUCTURED, Question.fieldConfig,
// AnswerValidator/ConfigurationValidator accepting it) actually carries a submitted answer through
// to each of the seven real answer-writers end to end - the writers themselves are the fixed,
// already-covered contract (see each writer's own header comment); this file is proving the new
// front door reaches them, not re-testing their internals.
@SpringBootTest
@Transactional
class StructuredQuestionIntegrationTest {

    @Autowired private WorkflowService workflowService;
    @Autowired private TemplateService templateService;
    @Autowired private QuestionService questionService;
    @Autowired private AssessmentService assessmentService;
    @Autowired private FamilyMemberService familyMemberService;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private QuestionnaireSessionRepository sessionRepository;
    @Autowired private DiseaseRepository diseaseRepository;
    @Autowired private MutationRepository mutationRepository;
    @Autowired private GenderTransitionRepository genderTransitionRepository;
    @Autowired private TwinshipMemberRepository twinshipMemberRepository;
    @Autowired private AdoptionRepository adoptionRepository;
    @Autowired private AdoptionParentRepository adoptionParentRepository;
    @Autowired private PregnancyOutcomeRepository pregnancyOutcomeRepository;
    @Autowired private AssistedReproductionRepository assistedReproductionRepository;
    @Autowired private AssistedReproductionParticipantRepository assistedReproductionParticipantRepository;

    private UUID sessionId;
    private UUID probandId;
    private UUID workflowId;

    @BeforeEach
    void seedWorkflow() {
        workflowId = createWorkflow(workflowService, "STRUCTURED_TEST_WF", 1);
    }

    // The question's template must exist BEFORE the session starts - AssessmentService.start()
    // resolves the proband's position immediately and throws if no workflow/template exists yet.
    // A FAMILY_MEMBER_ONLY-scoped template alone leaves the proband with nowhere to land, so one
    // is prefixed with a trivial PROBAND_ONLY template at an earlier displayOrder in that case.
    private UUID createStructuredQuestionAndStartSession(String templateCode, String answerKey, String fieldConfigJson, SubjectScope scope) {
        int displayOrder = 1;
        if (scope == SubjectScope.FAMILY_MEMBER_ONLY) {
            UUID probandLandingTemplateId = createTemplate(templateService, workflowId, templateCode + "_PROBAND_LANDING", 1, null, SubjectScope.PROBAND_ONLY);
            createQuestion(questionService, probandLandingTemplateId, templateCode + "_landing_q", QuestionType.TEXT, 1, null, null, null);
            displayOrder = 2;
        }
        UUID templateId = createTemplate(templateService, workflowId, templateCode, displayOrder, null, scope);
        UUID questionId = createQuestion(questionService, templateId, answerKey, QuestionType.STRUCTURED, 1,
                null, null, null, objectMapper.readTree(fieldConfigJson));
        sessionId = assessmentService.start(new AssessmentStartRequest(null)).sessionId();
        probandId = sessionRepository.findById(sessionId).orElseThrow().getPerson().getId();
        return questionId;
    }

    private void answer(UUID questionId, String jsonValue) {
        assessmentService.submitAnswer(sessionId, new AnswerSubmitRequest(questionId, objectMapper.readTree(jsonValue), null));
    }

    private void answerFor(UUID questionId, String jsonValue, UUID familyMemberId) {
        assessmentService.submitAnswer(sessionId, new AnswerSubmitRequest(questionId, objectMapper.readTree(jsonValue), familyMemberId));
    }

    private UUID createFamilyMember(RelationshipType type) {
        return familyMemberService.create(sessionId, new FamilyMemberCreateRequest(type, MaternalPaternalSideType.UNKNOWN)).id();
    }

    @Test
    void diseasesStructuredAnswerPersistsThroughTheRealWriter() {
        UUID questionId = createStructuredQuestionAndStartSession("CONDITIONS", "diseases", """
                {"shape":"list","fields":[
                  {"name":"diseaseType","label":"Disease name","type":"TEXT","required":true},
                  {"name":"ageAtDiagnosis","label":"Age at diagnosis","type":"NUMBER","required":false}
                ]}""", SubjectScope.BOTH);

        answer(questionId, "[{\"diseaseType\":\"Breast Cancer\",\"ageAtDiagnosis\":45}]");

        var diseases = diseaseRepository.findAllByPersonId(probandId);
        assertThat(diseases).hasSize(1);
        assertThat(diseases.get(0).getDiseaseCatalog().getName()).isEqualTo("Breast Cancer");
        assertThat(diseases.get(0).getAgeAtDiagnosis()).isEqualTo(45);
    }

    @Test
    void geneticFindingsStructuredAnswerPersistsThroughTheRealWriter() {
        UUID questionId = createStructuredQuestionAndStartSession("GENETIC_TESTING", "genetic_findings", """
                {"shape":"list","fields":[
                  {"name":"gene","label":"Gene","type":"TEXT","required":true},
                  {"name":"mutation","label":"Finding (free text)","type":"TEXT","required":false}
                ]}""", SubjectScope.BOTH);

        answer(questionId, "[{\"gene\":\"BRCA1\",\"mutation\":\"c.68_69delAG\"}]");

        var mutations = mutationRepository.findAllByPersonId(probandId);
        assertThat(mutations).hasSize(1);
        assertThat(mutations.get(0).getGene()).isEqualTo("BRCA1");
        assertThat(mutations.get(0).getVariant()).isEqualTo("c.68_69delAG");
    }

    @Test
    void genderTransitionStructuredAnswerPersistsThroughTheRealWriter() {
        UUID questionId = createStructuredQuestionAndStartSession("GENDER_TRANSITION", "gender_transition", """
                {"shape":"object","fields":[
                  {"name":"fromGender","label":"From","type":"DROPDOWN","required":true,
                   "options":[{"label":"Man","value":"MAN"},{"label":"Woman","value":"WOMAN"}]},
                  {"name":"toGender","label":"To","type":"DROPDOWN","required":true,
                   "options":[{"label":"Man","value":"MAN"},{"label":"Woman","value":"WOMAN"}]},
                  {"name":"transitionYear","label":"Year","type":"NUMBER","required":false}
                ]}""", SubjectScope.BOTH);

        answer(questionId, "{\"fromGender\":\"MAN\",\"toGender\":\"WOMAN\",\"transitionYear\":2020}");

        var transition = genderTransitionRepository.findByPersonId(probandId).orElseThrow();
        assertThat(transition.getFromGender().name()).isEqualTo("MAN");
        assertThat(transition.getToGender().name()).isEqualTo("WOMAN");
        assertThat(transition.getTransitionYear()).isEqualTo(2020);
    }

    @Test
    void twinshipStructuredAnswerPersistsThroughTheRealWriter() {
        UUID questionId = createStructuredQuestionAndStartSession("TWINSHIP", "twinship", """
                {"shape":"object","fields":[
                  {"name":"twinType","label":"Type","type":"DROPDOWN","required":true,
                   "options":[{"label":"Identical","value":"MONOZYGOTIC"}]},
                  {"name":"coTwinFamilyMemberIds","label":"Co-twins","type":"FAMILY_MEMBER_MULTI_SELECT","required":true}
                ]}""", SubjectScope.BOTH);
        UUID coTwinId = createFamilyMember(RelationshipType.BROTHER);

        answer(questionId, "{\"twinType\":\"MONOZYGOTIC\",\"coTwinFamilyMemberIds\":[\"" + coTwinId + "\"]}");

        assertThat(twinshipMemberRepository.findByPersonId(probandId)).isPresent();
        assertThat(twinshipMemberRepository.findByFamilyMemberId(coTwinId)).isPresent();
        UUID twinshipId = twinshipMemberRepository.findByPersonId(probandId).orElseThrow().getTwinship().getId();
        assertThat(twinshipMemberRepository.findAllByTwinshipId(twinshipId)).hasSize(2);
    }

    @Test
    void adoptionStructuredAnswerPersistsThroughTheRealWriter() {
        UUID questionId = createStructuredQuestionAndStartSession("ADOPTION", "adoption", """
                {"shape":"object","fields":[
                  {"name":"adoptionType","label":"Type","type":"DROPDOWN","required":true,
                   "options":[{"label":"Adopted in","value":"ADOPTED_IN"}]},
                  {"name":"adoptiveParentFamilyMemberIds","label":"Adoptive parents","type":"FAMILY_MEMBER_MULTI_SELECT","required":false}
                ]}""", SubjectScope.BOTH);
        UUID adoptiveParentId = createFamilyMember(RelationshipType.MOTHER);

        answer(questionId, "{\"adoptionType\":\"ADOPTED_IN\",\"adoptiveParentFamilyMemberIds\":[\"" + adoptiveParentId + "\"]}");

        var adoption = adoptionRepository.findByIndividualPersonId(probandId).orElseThrow();
        var parents = adoptionParentRepository.findAllByAdoptionId(adoption.getId());
        assertThat(parents).hasSize(1);
        assertThat(parents.get(0).getParentFamilyMember().getId()).isEqualTo(adoptiveParentId);
    }

    @Test
    void pregnancyOutcomeStructuredAnswerPersistsThroughTheRealWriter() {
        UUID questionId = createStructuredQuestionAndStartSession("PREGNANCY_OUTCOME", "pregnancy_outcome", """
                {"shape":"object","fields":[
                  {"name":"outcomeType","label":"Outcome","type":"DROPDOWN","required":true,
                   "options":[{"label":"Miscarriage","value":"SAB"}]},
                  {"name":"gestationalWeeks","label":"Weeks","type":"NUMBER","required":false},
                  {"name":"lmpDate","label":"LMP date","type":"DATE","required":false}
                ]}""", SubjectScope.FAMILY_MEMBER_ONLY);
        UUID familyMemberId = createFamilyMember(RelationshipType.SON);

        answerFor(questionId, "{\"outcomeType\":\"SAB\",\"gestationalWeeks\":10}", familyMemberId);

        var outcome = pregnancyOutcomeRepository.findByFamilyMemberId(familyMemberId).orElseThrow();
        assertThat(outcome.getOutcomeType().name()).isEqualTo("SAB");
        assertThat(outcome.getGestationalWeeks()).isEqualTo(10);
    }

    @Test
    void assistedReproductionStructuredAnswerWithEmptyParticipantsPersistsThroughTheRealWriter() {
        UUID questionId = createStructuredQuestionAndStartSession("ASSISTED_REPRODUCTION", "assisted_reproduction", """
                {"shape":"object","fields":[
                  {"name":"reproductionType","label":"Type","type":"DROPDOWN","required":true,
                   "options":[{"label":"Anonymous donor","value":"ANONYMOUS_DONOR"}]},
                  {"name":"anonymous","label":"Anonymous?","type":"DROPDOWN","required":false,
                   "options":[{"label":"Yes","value":true},{"label":"No","value":false}]},
                  {"name":"participants","label":"Participants","type":"GROUP_LIST","required":false,"fields":[
                     {"name":"role","label":"Role","type":"DROPDOWN","required":true,
                      "options":[{"label":"Egg donor","value":"EGG_DONOR"}]},
                     {"name":"familyMemberId","label":"Family member","type":"FAMILY_MEMBER_SELECT","required":true}
                  ]}
                ]}""", SubjectScope.BOTH);

        // Review Focus: an empty GROUP_LIST on a non-required field must not be rejected.
        answer(questionId, "{\"reproductionType\":\"ANONYMOUS_DONOR\",\"anonymous\":true,\"participants\":[]}");

        var record = assistedReproductionRepository.findByIndividualPersonId(probandId).orElseThrow();
        assertThat(record.getReproductionType().name()).isEqualTo("ANONYMOUS_DONOR");
        assertThat(record.getAnonymous()).isTrue();
        assertThat(assistedReproductionParticipantRepository.findAllByAssistedReproductionId(record.getId())).isEmpty();
    }

    @Test
    void malformedStructuredAnswerIsRejectedWithTheWritersOwnMessage() {
        UUID questionId = createStructuredQuestionAndStartSession("TWINSHIP_MALFORMED", "twinship", """
                {"shape":"object","fields":[
                  {"name":"twinType","label":"Type","type":"DROPDOWN","required":true,
                   "options":[{"label":"Identical","value":"MONOZYGOTIC"}]},
                  {"name":"coTwinFamilyMemberIds","label":"Co-twins","type":"FAMILY_MEMBER_MULTI_SELECT","required":true}
                ]}""", SubjectScope.BOTH);

        // Missing coTwinFamilyMemberIds entirely - TwinshipAnswerWriter requires at least one.
        assertThatThrownBy(() -> answer(questionId, "{\"twinType\":\"MONOZYGOTIC\"}"))
                .isInstanceOf(InvalidAnswerException.class)
                .hasMessageContaining("coTwinFamilyMemberIds");
    }
}
