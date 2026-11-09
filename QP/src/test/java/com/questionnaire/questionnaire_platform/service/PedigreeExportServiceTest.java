package com.questionnaire.questionnaire_platform.service;

import com.questionnaire.questionnaire_platform.dto.AnswerSubmitRequest;
import com.questionnaire.questionnaire_platform.dto.AssessmentStartRequest;
import com.questionnaire.questionnaire_platform.dto.FamilyMemberCreateRequest;
import com.questionnaire.questionnaire_platform.dto.ParentChildRelationshipCreateRequest;
import com.questionnaire.questionnaire_platform.dto.PartnerRelationshipCreateRequest;
import com.questionnaire.questionnaire_platform.entity.Adoption;
import com.questionnaire.questionnaire_platform.entity.AdoptionParent;
import com.questionnaire.questionnaire_platform.entity.AdoptionType;
import com.questionnaire.questionnaire_platform.entity.AssistedReproduction;
import com.questionnaire.questionnaire_platform.entity.AssistedReproductionParticipant;
import com.questionnaire.questionnaire_platform.entity.AssistedReproductionParticipantRole;
import com.questionnaire.questionnaire_platform.entity.AssistedReproductionType;
import com.questionnaire.questionnaire_platform.entity.Disease;
import com.questionnaire.questionnaire_platform.entity.DiseaseCatalog;
import com.questionnaire.questionnaire_platform.entity.DiseaseCodeSystemType;
import com.questionnaire.questionnaire_platform.entity.DiseaseLateralityType;
import com.questionnaire.questionnaire_platform.entity.DiseaseStatusType;
import com.questionnaire.questionnaire_platform.entity.FamilyMember;
import com.questionnaire.questionnaire_platform.entity.GenderIdentityType;
import com.questionnaire.questionnaire_platform.entity.GenderTransition;
import com.questionnaire.questionnaire_platform.entity.MaternalPaternalSideType;
import com.questionnaire.questionnaire_platform.entity.Mutation;
import com.questionnaire.questionnaire_platform.entity.MutationResultType;
import com.questionnaire.questionnaire_platform.entity.Person;
import com.questionnaire.questionnaire_platform.entity.PregnancyOutcome;
import com.questionnaire.questionnaire_platform.entity.PregnancyOutcomeType;
import com.questionnaire.questionnaire_platform.entity.QuestionType;
import com.questionnaire.questionnaire_platform.entity.RelationshipStatusType;
import com.questionnaire.questionnaire_platform.entity.RelationshipType;
import com.questionnaire.questionnaire_platform.entity.SubjectScope;
import com.questionnaire.questionnaire_platform.entity.Twinship;
import com.questionnaire.questionnaire_platform.entity.TwinshipMember;
import com.questionnaire.questionnaire_platform.entity.TwinType;
import com.questionnaire.questionnaire_platform.repository.AdoptionParentRepository;
import com.questionnaire.questionnaire_platform.repository.AdoptionRepository;
import com.questionnaire.questionnaire_platform.repository.AssistedReproductionParticipantRepository;
import com.questionnaire.questionnaire_platform.repository.AssistedReproductionRepository;
import com.questionnaire.questionnaire_platform.repository.DiseaseCatalogRepository;
import com.questionnaire.questionnaire_platform.repository.DiseaseRepository;
import com.questionnaire.questionnaire_platform.repository.FamilyMemberRepository;
import com.questionnaire.questionnaire_platform.repository.GenderTransitionRepository;
import com.questionnaire.questionnaire_platform.repository.MutationRepository;
import com.questionnaire.questionnaire_platform.repository.PersonRepository;
import com.questionnaire.questionnaire_platform.repository.PregnancyOutcomeRepository;
import com.questionnaire.questionnaire_platform.repository.TwinshipMemberRepository;
import com.questionnaire.questionnaire_platform.repository.TwinshipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static com.questionnaire.questionnaire_platform.support.TestFixtures.createQuestion;
import static com.questionnaire.questionnaire_platform.support.TestFixtures.createTemplate;
import static com.questionnaire.questionnaire_platform.support.TestFixtures.createWorkflow;
import static org.assertj.core.api.Assertions.assertThat;

// Covers PedigreeExportService against a 3-generation graph (grandparents -> parents -> proband +
// siblings), deliberately including the cases the reference sample
// (family_of_10_newschema_V4.json) doesn't exercise on its own: a consanguineous couple (tests the
// provisional matingLineCode derivation), a half-sibling (tests halfsiblings{} keying), and a
// spouse who "married in" with no known parents of their own (tests the generation-unit
// propagation, not just plain parent-child depth).
@SpringBootTest
@Transactional
class PedigreeExportServiceTest {

    @Autowired private WorkflowService workflowService;
    @Autowired private TemplateService templateService;
    @Autowired private QuestionService questionService;
    @Autowired private AssessmentService assessmentService;
    @Autowired private FamilyMemberService familyMemberService;
    @Autowired private ParentChildRelationshipService parentChildRelationshipService;
    @Autowired private PartnerRelationshipService partnerRelationshipService;
    @Autowired private PedigreeExportService pedigreeExportService;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private com.questionnaire.questionnaire_platform.repository.QuestionnaireSessionRepository sessionRepository;
    @Autowired private PersonRepository personRepository;
    @Autowired private FamilyMemberRepository familyMemberRepository;
    @Autowired private DiseaseRepository diseaseRepository;
    @Autowired private DiseaseCatalogRepository diseaseCatalogRepository;
    @Autowired private MutationRepository mutationRepository;
    @Autowired private GenderTransitionRepository genderTransitionRepository;
    @Autowired private TwinshipRepository twinshipRepository;
    @Autowired private TwinshipMemberRepository twinshipMemberRepository;
    @Autowired private AdoptionRepository adoptionRepository;
    @Autowired private AdoptionParentRepository adoptionParentRepository;
    @Autowired private PregnancyOutcomeRepository pregnancyOutcomeRepository;
    @Autowired private AssistedReproductionRepository assistedReproductionRepository;
    @Autowired private AssistedReproductionParticipantRepository assistedReproductionParticipantRepository;

    private UUID sessionId;
    private UUID firstNameQid;
    private UUID lastNameQid;
    private UUID sexQid;
    private UUID deceasedQid;
    private UUID ageAtDeathQid;

    private UUID probandId;
    private UUID motherId;
    private UUID fatherId;
    private UUID siblingId;
    private UUID halfSiblingId;
    private UUID grandmotherId;
    private UUID grandfatherId;

    @BeforeEach
    void seedFamily() {
        UUID workflowId = createWorkflow(workflowService, "PEDIGREE_TEST_WF", 1);
        UUID templateId = createTemplate(templateService, workflowId, "PEDIGREE_TEST_BASIC", 1, null, SubjectScope.BOTH);
        firstNameQid = createQuestion(questionService, templateId, "first_name", QuestionType.TEXT, 1, null, null, null);
        lastNameQid = createQuestion(questionService, templateId, "last_name", QuestionType.TEXT, 2, null, null, null);
        sexQid = createQuestion(questionService, templateId, "sex_assigned_at_birth", QuestionType.RADIO, 3, null, null, null);
        deceasedQid = createQuestion(questionService, templateId, "deceased", QuestionType.RADIO, 4, null, null, null);
        ageAtDeathQid = createQuestion(questionService, templateId, "age_at_death", QuestionType.NUMBER, 5, null, null, null);

        sessionId = assessmentService.start(new AssessmentStartRequest(null)).sessionId();
        probandId = sessionRepository.findById(sessionId).orElseThrow().getPerson().getId();
        answer(firstNameQid, "\"Noah\"");
        answer(lastNameQid, "\"Test\"");
        answer(sexQid, "\"MALE\"");

        motherId = addFamilyMember(RelationshipType.MOTHER, MaternalPaternalSideType.MATERNAL, "Sarah", "FEMALE");
        fatherId = addFamilyMember(RelationshipType.FATHER, MaternalPaternalSideType.PATERNAL, "Michael", "MALE");
        siblingId = addFamilyMember(RelationshipType.BROTHER, MaternalPaternalSideType.UNKNOWN, "Liam", "MALE");
        halfSiblingId = addFamilyMember(RelationshipType.HALF_BROTHER, MaternalPaternalSideType.MATERNAL, "Ethan", "MALE");
        grandmotherId = addFamilyMember(RelationshipType.GRANDMOTHER, MaternalPaternalSideType.MATERNAL, "Rose", "FEMALE");
        grandfatherId = addFamilyMemberDeceased(RelationshipType.GRANDFATHER, MaternalPaternalSideType.MATERNAL, "George", "MALE", 85);

        // sibling shares both parents with the proband -> full sibling
        parentChildRelationshipService.create(sessionId, new ParentChildRelationshipCreateRequest(motherId, siblingId));
        parentChildRelationshipService.create(sessionId, new ParentChildRelationshipCreateRequest(fatherId, siblingId));
        // half-sibling shares only the mother -> half sibling via mother
        parentChildRelationshipService.create(sessionId, new ParentChildRelationshipCreateRequest(motherId, halfSiblingId));
        // grandparents are the mother's parents
        parentChildRelationshipService.create(sessionId, new ParentChildRelationshipCreateRequest(grandmotherId, motherId));
        parentChildRelationshipService.create(sessionId, new ParentChildRelationshipCreateRequest(grandfatherId, motherId));

        // mother+father: married, not consanguineous -> matingLineCode should fall through to matingLine
        partnerRelationshipService.create(sessionId, new PartnerRelationshipCreateRequest(
                motherId, fatherId, null, null, null, RelationshipStatusType.MARRIED, false, null, null));
        // grandmother+grandfather: consanguineous -> matingLineCode should be "consanguineous"
        partnerRelationshipService.create(sessionId, new PartnerRelationshipCreateRequest(
                grandmotherId, grandfatherId, null, null, null, null, true, "First cousins", null));
    }

    private void answer(UUID questionId, String jsonValue) {
        assessmentService.submitAnswer(sessionId, new AnswerSubmitRequest(questionId, objectMapper.readTree(jsonValue), null));
    }

    private void answerFor(UUID questionId, String jsonValue, UUID familyMemberId) {
        assessmentService.submitAnswer(sessionId, new AnswerSubmitRequest(questionId, objectMapper.readTree(jsonValue), familyMemberId));
    }

    private UUID addFamilyMember(RelationshipType type, MaternalPaternalSideType side, String firstName, String sex) {
        UUID id = familyMemberService.create(sessionId, new FamilyMemberCreateRequest(type, side)).id();
        assessmentService.currentTemplate(sessionId, id); // resolves her entry template
        answerFor(firstNameQid, "\"" + firstName + "\"", id);
        answerFor(lastNameQid, "\"Test\"", id);
        answerFor(sexQid, "\"" + sex + "\"", id);
        assessmentService.nextTemplate(sessionId, id);
        return id;
    }

    private UUID addFamilyMemberDeceased(RelationshipType type, MaternalPaternalSideType side, String firstName, String sex, int ageAtDeath) {
        UUID id = familyMemberService.create(sessionId, new FamilyMemberCreateRequest(type, side)).id();
        assessmentService.currentTemplate(sessionId, id); // resolves her entry template
        answerFor(firstNameQid, "\"" + firstName + "\"", id);
        answerFor(lastNameQid, "\"Test\"", id);
        answerFor(sexQid, "\"" + sex + "\"", id);
        answerFor(deceasedQid, "true", id);
        answerFor(ageAtDeathQid, String.valueOf(ageAtDeath), id);
        assessmentService.nextTemplate(sessionId, id);
        return id;
    }

    private JsonNode findByName(JsonNode individuals, String name) {
        for (JsonNode individual : individuals) {
            if (individual.path("name").asString("").equals(name)) {
                return individual;
            }
        }
        throw new AssertionError("No individual named '" + name + "' in export: " + individuals);
    }

    @Test
    void exportsASchemaConformantDocumentWithCorrectGraphStructure() {
        JsonNode document = pedigreeExportService.export(sessionId);

        assertThat(document.path("schemaVersion").asString()).isEqualTo("4.0.0");
        assertThat(document.path("title").asString()).contains("Noah Test");
        assertThat(document.path("diseases").isArray()).isTrue();
        assertThat(document.path("diseases")).isEmpty();

        JsonNode individualsMap = document.path("individuals");
        assertThat(individualsMap.isObject()).isTrue();
        // proband + mother + father + sibling + halfSibling + grandmother + grandfather = 7
        assertThat(individualsMap.properties()).hasSize(7);

        java.util.List<JsonNode> individuals = new java.util.ArrayList<>();
        individualsMap.forEach(individuals::add);

        JsonNode noah = findByName(individualsMap, "Noah Test");
        JsonNode sarah = findByName(individualsMap, "Sarah Test");
        JsonNode michael = findByName(individualsMap, "Michael Test");
        JsonNode liam = findByName(individualsMap, "Liam Test");
        JsonNode ethan = findByName(individualsMap, "Ethan Test");
        JsonNode rose = findByName(individualsMap, "Rose Test");
        JsonNode george = findByName(individualsMap, "George Test");

        // --- individualLabel pattern + generation layering ---
        assertThat(rose.path("individualLabel").asString()).matches("^I-\\d+$");
        assertThat(george.path("individualLabel").asString()).matches("^I-\\d+$");
        assertThat(rose.path("generation").asInt()).isEqualTo(0);
        assertThat(george.path("generation").asInt()).isEqualTo(0);

        assertThat(sarah.path("individualLabel").asString()).matches("^II-\\d+$");
        assertThat(sarah.path("generation").asInt()).isEqualTo(1);
        // Michael has no known parents of his own, but married Sarah (generation 1 via her
        // parents) - his generation must be inherited through the spouse edge, not left at 0.
        assertThat(michael.path("generation").asInt()).isEqualTo(1);
        assertThat(michael.path("individualLabel").asString()).matches("^II-\\d+$");

        assertThat(noah.path("generation").asInt()).isEqualTo(2);
        assertThat(liam.path("generation").asInt()).isEqualTo(2);
        assertThat(ethan.path("generation").asInt()).isEqualTo(2);
        assertThat(noah.path("individualLabel").asString()).matches("^III-\\d+$");

        // --- role ---
        assertThat(noah.path("role").asString()).isEqualTo("proband");
        assertThat(sarah.path("role").asString()).isEqualTo("none");

        // --- sex/gender mapping ---
        assertThat(noah.path("sex").asInt()).isEqualTo(1); // MALE
        assertThat(sarah.path("sex").asInt()).isEqualTo(2); // FEMALE

        // --- parents/children/spouses wiring ---
        int sarahId = sarah.path("id").asInt();
        int michaelId = michael.path("id").asInt();
        int roseId = rose.path("id").asInt();
        int georgeId = george.path("id").asInt();

        assertThat(noah.path("parents").valueStream().map(JsonNode::asInt)).containsExactlyInAnyOrder(sarahId, michaelId);
        assertThat(sarah.path("parents").valueStream().map(JsonNode::asInt)).containsExactlyInAnyOrder(roseId, georgeId);
        assertThat(michael.path("parents")).isEmpty();

        // mother's spouses entry keyed by father's id
        assertThat(sarah.path("spouses").has(String.valueOf(michaelId))).isTrue();
        JsonNode sarahSpouseEntry = sarah.path("spouses").path(String.valueOf(michaelId));
        assertThat(sarahSpouseEntry.path("matingLineCode").asString()).isEqualTo("matingLine");
        assertThat(sarahSpouseEntry.has("consanguinityDegree")).isFalse(); // omitted, not null, when not consanguineous

        // grandparents: consanguineous -> matingLineCode wins over the default
        JsonNode roseSpouseEntry = rose.path("spouses").path(String.valueOf(georgeId));
        assertThat(roseSpouseEntry.path("matingLineCode").asString()).isEqualTo("consanguineous");
        assertThat(roseSpouseEntry.path("consanguinityDegree").asString()).isEqualTo("First cousins");

        // children keyed by co-parent
        assertThat(sarah.path("children").path(String.valueOf(michaelId)).valueStream().map(JsonNode::asInt))
                .containsExactlyInAnyOrder(noah.path("id").asInt(), liam.path("id").asInt());
        // half-sibling's only known parent is the mother -> bucketed under her own id (no known co-parent)
        assertThat(sarah.path("children").path(String.valueOf(sarahId)).valueStream().map(JsonNode::asInt))
                .containsExactly(ethan.path("id").asInt());

        // --- siblings / halfsiblings ---
        assertThat(noah.path("siblings").valueStream().map(JsonNode::asInt)).containsExactly(liam.path("id").asInt());
        assertThat(noah.path("halfsiblings").has(String.valueOf(sarahId))).isTrue();
        assertThat(noah.path("halfsiblings").path(String.valueOf(sarahId)).valueStream().map(JsonNode::asInt))
                .containsExactly(ethan.path("id").asInt());

        // --- deceased / lifeStatus / age-at-death ---
        assertThat(george.path("lifeStatus").asString()).isEqualTo("deceased");
        assertThat(george.path("age").asInt()).isEqualTo(85);
        // deceased was never answered for Sarah - "unknown" is the correct tri-state default
        // (not answering it IS the third state, per the deceased-field design locked 2026-10-14).
        assertThat(sarah.path("lifeStatus").asString()).isEqualTo("unknown");

        // --- Section G / unbuilt-entity fields stay null, never fabricated ---
        assertThat(noah.path("twinship").isNull()).isTrue();
        assertThat(noah.path("adoption").isNull()).isTrue();
        assertThat(noah.path("conditions")).isEmpty();
        assertThat(noah.path("affectedAndCarrier").valueStream().map(JsonNode::asString)).containsExactly("unaffected");
    }

    @Test
    void aFamilyMemberWithNoRelationshipEdgesExportsAsADisconnectedNode() {
        // An EXTENDED_FAMILY member added without any follow-up relationship call - should still
        // export validly, just disconnected, not silently dropped or crashed on.
        UUID isolatedId = familyMemberService.create(sessionId,
                new FamilyMemberCreateRequest(RelationshipType.COUSIN, MaternalPaternalSideType.UNKNOWN)).id();
        answerFor(firstNameQid, "\"Isolated\"", isolatedId);
        assessmentService.nextTemplate(sessionId, isolatedId);

        JsonNode document = pedigreeExportService.export(sessionId);
        JsonNode isolated = findByName(document.path("individuals"), "Isolated");
        assertThat(isolated.path("parents")).isEmpty();
        assertThat(isolated.path("spouses")).isEmpty();
        assertThat(isolated.path("children")).isEmpty();
        assertThat(isolated.path("generation").asInt()).isEqualTo(0);
    }

    private DiseaseCatalog createDiseaseCatalog(String name) {
        DiseaseCatalog catalog = new DiseaseCatalog();
        catalog.setName(name);
        LocalDateTime now = LocalDateTime.now();
        catalog.setCreatedAt(now);
        catalog.setUpdatedAt(now);
        return diseaseCatalogRepository.save(catalog);
    }

    private void addDisease(UUID personId, UUID familyMemberId, DiseaseCatalog catalog, Integer ageAtDiagnosis,
                             DiseaseLateralityType laterality, DiseaseStatusType status) {
        Disease disease = new Disease();
        if (personId != null) {
            disease.setPerson(personRepository.findById(personId).orElseThrow());
        } else {
            disease.setFamilyMember(familyMemberRepository.findById(familyMemberId).orElseThrow());
        }
        disease.setDiseaseCatalog(catalog);
        disease.setCodeSystem(DiseaseCodeSystemType.ICD_10);
        disease.setCodeId("C50.9");
        disease.setAgeAtDiagnosis(ageAtDiagnosis);
        disease.setLaterality(laterality);
        disease.setStatus(status);
        diseaseRepository.save(disease);
    }

    @Test
    void conditionsAndGeneticTestsExportWithAffectedAndCarrierDerivation() {
        DiseaseCatalog breastCancer = createDiseaseCatalog("Breast Cancer");
        addDisease(probandId, null, breastCancer, 45, DiseaseLateralityType.LEFT, DiseaseStatusType.CONFIRMED);

        Mutation mutation = new Mutation();
        mutation.setPerson(personRepository.findById(probandId).orElseThrow());
        mutation.setGene("BRCA1");
        mutation.setResult(MutationResultType.PATHOGENIC);
        mutation.setVariant("c.68_69delAG");
        mutationRepository.save(mutation);

        JsonNode document = pedigreeExportService.export(sessionId);
        JsonNode noah = findByName(document.path("individuals"), "Noah Test");

        JsonNode condition = noah.path("conditions").get(0);
        assertThat(condition.path("diseaseRefId").asInt()).isEqualTo(1);
        assertThat(condition.path("code").path("system").asString()).isEqualTo("ICD-10");
        assertThat(condition.path("code").path("id").asString()).isEqualTo("C50.9");
        assertThat(condition.path("ageAtDiagnosis").asInt()).isEqualTo(45);
        assertThat(condition.path("laterality").asString()).isEqualTo("left");
        assertThat(condition.path("status").asString()).isEqualTo("confirmed");

        JsonNode test = noah.path("geneticTests").get(0);
        assertThat(test.path("gene").asString()).isEqualTo("BRCA1");
        assertThat(test.path("result").asString()).isEqualTo("pathogenic");
        assertThat(test.path("variant").asString()).isEqualTo("c.68_69delAG");

        assertThat(noah.path("affectedAndCarrier").valueStream().map(JsonNode::asString)).containsExactly("affected");

        JsonNode diseasesCatalog = document.path("diseases").get(0);
        assertThat(diseasesCatalog.path("id").asInt()).isEqualTo(1);
        assertThat(diseasesCatalog.path("name").asString()).isEqualTo("Breast Cancer");
        assertThat(diseasesCatalog.path("quadrant").asString()).isEqualTo("upperLeft");
    }

    @Test
    void pathogenicMutationWithNoConditionsDerivesCarrierHet() {
        Mutation mutation = new Mutation();
        mutation.setPerson(personRepository.findById(probandId).orElseThrow());
        mutation.setGene("BRCA2");
        mutation.setResult(MutationResultType.LIKELY_PATHOGENIC);
        mutationRepository.save(mutation);

        JsonNode document = pedigreeExportService.export(sessionId);
        JsonNode noah = findByName(document.path("individuals"), "Noah Test");

        assertThat(noah.path("conditions")).isEmpty();
        assertThat(noah.path("affectedAndCarrier").valueStream().map(JsonNode::asString)).containsExactly("carrierHet");
    }

    @Test
    void documentLevelDiseaseCatalogCapsAtFourDistinctEntriesWithClockwiseQuadrants() {
        addDisease(probandId, null, createDiseaseCatalog("Disease A"), null, null, DiseaseStatusType.REPORTED);
        addDisease(null, motherId, createDiseaseCatalog("Disease B"), null, null, DiseaseStatusType.REPORTED);
        addDisease(null, fatherId, createDiseaseCatalog("Disease C"), null, null, DiseaseStatusType.REPORTED);
        addDisease(null, siblingId, createDiseaseCatalog("Disease D"), null, null, DiseaseStatusType.REPORTED);
        addDisease(null, halfSiblingId, createDiseaseCatalog("Disease E"), null, null, DiseaseStatusType.REPORTED);

        JsonNode document = pedigreeExportService.export(sessionId);

        assertThat(document.path("diseases")).hasSize(4);
        assertThat(document.path("diseases").valueStream().map(d -> d.path("quadrant").asString()))
                .containsExactly("upperLeft", "upperRight", "lowerRight", "lowerLeft");

        // The 5th distinct disease (Ethan's) didn't make the cut - his condition is still exported,
        // just with no diseaseRefId into the capped catalog.
        JsonNode ethan = findByName(document.path("individuals"), "Ethan Test");
        assertThat(ethan.path("conditions").get(0).path("diseaseRefId").isNull()).isTrue();
        assertThat(ethan.path("conditions").get(0).path("code").path("id").asString()).isEqualTo("C50.9");
    }

    @Test
    void genderTransitionExportsWhenPresentAndIsOmittedWhenAbsent() {
        GenderTransition transition = new GenderTransition();
        transition.setFamilyMember(familyMemberRepository.findById(motherId).orElseThrow());
        transition.setFromGender(GenderIdentityType.MAN);
        transition.setToGender(GenderIdentityType.WOMAN);
        transition.setTransitionYear(2010);
        transition.setCreatedAt(LocalDateTime.now());
        genderTransitionRepository.save(transition);

        JsonNode document = pedigreeExportService.export(sessionId);
        JsonNode sarah = findByName(document.path("individuals"), "Sarah Test");
        assertThat(sarah.path("genderTransition").path("fromGender").asInt()).isEqualTo(1);
        assertThat(sarah.path("genderTransition").path("toGender").asInt()).isEqualTo(2);
        assertThat(sarah.path("genderTransition").path("transitionYear").asInt()).isEqualTo(2010);

        JsonNode michael = findByName(document.path("individuals"), "Michael Test");
        assertThat(michael.has("genderTransition")).isFalse();
    }

    @Test
    void twinshipExportsTypeAndCoTwinsExcludingSelf() {
        Twinship twinship = new Twinship();
        twinship.setProband(personRepository.findById(probandId).orElseThrow());
        twinship.setTwinType(TwinType.DIZYGOTIC);
        twinship.setCreatedAt(LocalDateTime.now());
        twinship = twinshipRepository.save(twinship);

        TwinshipMember memberA = new TwinshipMember();
        memberA.setTwinship(twinship);
        memberA.setFamilyMember(familyMemberRepository.findById(siblingId).orElseThrow());
        twinshipMemberRepository.save(memberA);

        TwinshipMember memberB = new TwinshipMember();
        memberB.setTwinship(twinship);
        memberB.setFamilyMember(familyMemberRepository.findById(halfSiblingId).orElseThrow());
        twinshipMemberRepository.save(memberB);

        JsonNode document = pedigreeExportService.export(sessionId);
        JsonNode liam = findByName(document.path("individuals"), "Liam Test");
        assertThat(liam.path("twinship").path("type").asString()).isEqualTo("twinsDizygotic");
        JsonNode ethan = findByName(document.path("individuals"), "Ethan Test");
        assertThat(liam.path("twinship").path("coTwins").valueStream().map(JsonNode::asInt))
                .containsExactly(ethan.path("id").asInt());

        JsonNode noah = findByName(document.path("individuals"), "Noah Test");
        assertThat(noah.path("twinship").isNull()).isTrue();
    }

    @Test
    void adoptionExportsAdoptiveParentsAndDerivesBiologicalParentsFromTheExistingGraph() {
        UUID stepDadId = addFamilyMember(RelationshipType.UNCLE, MaternalPaternalSideType.UNKNOWN, "Step", "MALE");

        Adoption adoption = new Adoption();
        adoption.setProband(personRepository.findById(probandId).orElseThrow());
        adoption.setIndividualPerson(personRepository.findById(probandId).orElseThrow());
        adoption.setAdoptionType(AdoptionType.ADOPTED_IN);
        adoption.setCreatedAt(LocalDateTime.now());
        adoption = adoptionRepository.save(adoption);

        AdoptionParent adoptiveParent = new AdoptionParent();
        adoptiveParent.setAdoption(adoption);
        adoptiveParent.setParentFamilyMember(familyMemberRepository.findById(stepDadId).orElseThrow());
        adoptionParentRepository.save(adoptiveParent);

        JsonNode document = pedigreeExportService.export(sessionId);
        JsonNode noah = findByName(document.path("individuals"), "Noah Test");
        JsonNode stepDad = findByName(document.path("individuals"), "Step Test");
        JsonNode sarah = findByName(document.path("individuals"), "Sarah Test");
        JsonNode michael = findByName(document.path("individuals"), "Michael Test");

        assertThat(noah.path("adoption").path("type").asString()).isEqualTo("adoptedIn");
        assertThat(noah.path("adoption").path("adoptiveParents").valueStream().map(JsonNode::asInt))
                .containsExactly(stepDad.path("id").asInt());
        // Biological parents are never stored on ADOPTION_PARENT - derived from the same graph used
        // for Noah's own "parents" field (mother/father, already in the fixture).
        assertThat(noah.path("adoption").path("biologicalParents").valueStream().map(JsonNode::asInt))
                .containsExactlyInAnyOrder(sarah.path("id").asInt(), michael.path("id").asInt());
        assertThat(noah.path("adoption").path("biologicalParents").valueStream().map(JsonNode::asInt))
                .containsExactlyInAnyOrderElementsOf(
                        noah.path("parents").valueStream().map(JsonNode::asInt).toList());
    }

    @Test
    void pregnancyOutcomeExportsForAFamilyMember() {
        UUID lossId = addFamilyMember(RelationshipType.PREGNANCY_LOSS, MaternalPaternalSideType.UNKNOWN, "Unnamed", "UNKNOWN");

        PregnancyOutcome outcome = new PregnancyOutcome();
        outcome.setFamilyMember(familyMemberRepository.findById(lossId).orElseThrow());
        outcome.setOutcomeType(PregnancyOutcomeType.SAB);
        outcome.setGestationalWeeks(10);
        outcome.setLmpDate(LocalDate.of(2020, 1, 1));
        outcome.setCreatedAt(LocalDateTime.now());
        pregnancyOutcomeRepository.save(outcome);

        JsonNode document = pedigreeExportService.export(sessionId);
        JsonNode loss = findByName(document.path("individuals"), "Unnamed Test");
        assertThat(loss.path("pregnancyOutcome").path("type").asString()).isEqualTo("sab");
        assertThat(loss.path("pregnancyOutcome").path("gestationalWeeks").asInt()).isEqualTo(10);
        assertThat(loss.path("pregnancyOutcome").path("lmpDate").asString()).isEqualTo("2020-01-01");
    }

    @Test
    void assistedReproductionExportsParticipantRolesResolvedToExportIds() {
        UUID donorId = addFamilyMember(RelationshipType.COUSIN, MaternalPaternalSideType.UNKNOWN, "Donor", "FEMALE");

        AssistedReproduction ar = new AssistedReproduction();
        ar.setProband(personRepository.findById(probandId).orElseThrow());
        ar.setIndividualPerson(personRepository.findById(probandId).orElseThrow());
        ar.setReproductionType(AssistedReproductionType.IVF);
        ar.setAnonymous(false);
        ar.setCreatedAt(LocalDateTime.now());
        ar = assistedReproductionRepository.save(ar);

        AssistedReproductionParticipant participant = new AssistedReproductionParticipant();
        participant.setAssistedReproduction(ar);
        participant.setParticipantRole(AssistedReproductionParticipantRole.EGG_DONOR);
        participant.setParticipantFamilyMember(familyMemberRepository.findById(donorId).orElseThrow());
        assistedReproductionParticipantRepository.save(participant);

        JsonNode document = pedigreeExportService.export(sessionId);
        JsonNode noah = findByName(document.path("individuals"), "Noah Test");
        JsonNode donor = findByName(document.path("individuals"), "Donor Test");

        assertThat(noah.path("assistedReproduction").path("type").asString()).isEqualTo("ivf");
        assertThat(noah.path("assistedReproduction").path("anonymous").asBoolean()).isFalse();
        assertThat(noah.path("assistedReproduction").path("eggDonorId").asInt()).isEqualTo(donor.path("id").asInt());
    }
}
