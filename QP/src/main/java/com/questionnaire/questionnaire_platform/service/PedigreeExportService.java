package com.questionnaire.questionnaire_platform.service;

import com.questionnaire.questionnaire_platform.entity.Adoption;
import com.questionnaire.questionnaire_platform.entity.AdoptionParent;
import com.questionnaire.questionnaire_platform.entity.AssistedReproduction;
import com.questionnaire.questionnaire_platform.entity.AssistedReproductionParticipant;
import com.questionnaire.questionnaire_platform.entity.Disease;
import com.questionnaire.questionnaire_platform.entity.DiseaseCatalog;
import com.questionnaire.questionnaire_platform.entity.FamilyMember;
import com.questionnaire.questionnaire_platform.entity.FamilyMemberRole;
import com.questionnaire.questionnaire_platform.entity.GenderIdentityType;
import com.questionnaire.questionnaire_platform.entity.GenderTransition;
import com.questionnaire.questionnaire_platform.entity.MatingLineType;
import com.questionnaire.questionnaire_platform.entity.Mutation;
import com.questionnaire.questionnaire_platform.entity.MutationResultType;
import com.questionnaire.questionnaire_platform.entity.ParentChildRelationship;
import com.questionnaire.questionnaire_platform.entity.PartnerRelationship;
import com.questionnaire.questionnaire_platform.entity.Person;
import com.questionnaire.questionnaire_platform.entity.PregnancyOutcome;
import com.questionnaire.questionnaire_platform.entity.QuestionnaireSession;
import com.questionnaire.questionnaire_platform.entity.RelationshipStatusType;
import com.questionnaire.questionnaire_platform.entity.RelationshipType;
import com.questionnaire.questionnaire_platform.entity.ReproductiveStatusType;
import com.questionnaire.questionnaire_platform.entity.SexAssignedAtBirthType;
import com.questionnaire.questionnaire_platform.entity.TwinshipMember;
import com.questionnaire.questionnaire_platform.exception.ResourceNotFoundException;
import com.questionnaire.questionnaire_platform.repository.AdoptionParentRepository;
import com.questionnaire.questionnaire_platform.repository.AdoptionRepository;
import com.questionnaire.questionnaire_platform.repository.AssistedReproductionParticipantRepository;
import com.questionnaire.questionnaire_platform.repository.AssistedReproductionRepository;
import com.questionnaire.questionnaire_platform.repository.DiseaseRepository;
import com.questionnaire.questionnaire_platform.repository.FamilyMemberRepository;
import com.questionnaire.questionnaire_platform.repository.FamilyMemberRoleRepository;
import com.questionnaire.questionnaire_platform.repository.GenderTransitionRepository;
import com.questionnaire.questionnaire_platform.repository.MutationRepository;
import com.questionnaire.questionnaire_platform.repository.ParentChildRelationshipRepository;
import com.questionnaire.questionnaire_platform.repository.PartnerRelationshipRepository;
import com.questionnaire.questionnaire_platform.repository.PregnancyOutcomeRepository;
import com.questionnaire.questionnaire_platform.repository.QuestionnaireSessionRepository;
import com.questionnaire.questionnaire_platform.repository.TwinshipMemberRepository;
import com.questionnaire.questionnaire_platform.util.JsonColumnMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// Converts one proband's QP questionnaire data (PERSON/FAMILY_MEMBER/PARENT_CHILD_RELATIONSHIP/
// PARTNER_RELATIONSHIP) into a document conforming to pedigree_schema.json (schemaVersion 4.0.0,
// PSWG/Bennett 2022+2025) - the format the separately-built "PedigreeDraw" tool consumes. This is
// "task 4" from the 2026-09-28 manager assignment - everything built before this service
// (relationship tables, subject-scope execution, the sex/gender model, the question set) was
// prerequisite groundwork for this export, not the export itself.
//
// v2 (2026-10-30): wires up conditions[]/geneticTests[]/the document-level diseases[] catalog/
// twinship/adoption/pregnancyOutcome/assistedReproduction/genderTransition, reading from the
// pedigree-extension tables added alongside this. See QP-Pedigree-Export.md for the full history.
// affectedAndCarrier is derived: affected/affectedMultiple from conditions[].length, else carrierHet
// when a PATHOGENIC/LIKELY_PATHOGENIC geneticTests[] entry exists, else unaffected.
// Still deliberately out of scope:
//   - obligateCarrier - requires pedigree-structural inheritance inference (who else in the
//     pedigree is affected/related), not just this individual's own records (§6.2).
//   - a distinct asymptomaticCarrier - would overlap with carrierHet with no separate signal in the
//     current data to tell the two apart (§6.2).
//   - extensions.cancerRiskModel - CANCER_RISK_PROFILE doesn't exist, scope still pending.
//   - inferredAncestry is always null - no genetic ancestry inference is performed.
//   - settings/_coverageMap are omitted entirely (optional, not nullable - confirmed against the
//     schema's own top-level `required` list).
//   - generation/individualLabel are computed from the relationship graph, not stored - an
//     individual with no created relationship edges (e.g. an EXTENDED_FAMILY member added without
//     a follow-up POST .../relationships/parent-child call) exports as a disconnected node at
//     generation 0, honestly reflecting that no edge was ever created for them.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PedigreeExportService {

    private final QuestionnaireSessionRepository sessionRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final FamilyMemberRoleRepository familyMemberRoleRepository;
    private final ParentChildRelationshipRepository parentChildRelationshipRepository;
    private final PartnerRelationshipRepository partnerRelationshipRepository;
    private final DiseaseRepository diseaseRepository;
    private final MutationRepository mutationRepository;
    private final GenderTransitionRepository genderTransitionRepository;
    private final TwinshipMemberRepository twinshipMemberRepository;
    private final AdoptionRepository adoptionRepository;
    private final AdoptionParentRepository adoptionParentRepository;
    private final PregnancyOutcomeRepository pregnancyOutcomeRepository;
    private final AssistedReproductionRepository assistedReproductionRepository;
    private final AssistedReproductionParticipantRepository assistedReproductionParticipantRepository;
    private final JsonColumnMapper jsonColumnMapper;
    private final ObjectMapper objectMapper;

    public JsonNode export(UUID sessionId) {
        QuestionnaireSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session " + sessionId + " not found"));
        Person proband = session.getPerson();
        UUID probandId = proband.getId();

        List<FamilyMember> familyMembers = familyMemberRepository.findAllByProbandIdOrderByCreatedAtAsc(probandId);
        List<ParentChildRelationship> parentChildRows = parentChildRelationshipRepository.findAllByProbandIdOrderByCreatedAtAsc(probandId);
        List<PartnerRelationship> partnerRows = partnerRelationshipRepository.findAllByProbandIdOrderByCreatedAtAsc(probandId);
        List<FamilyMemberRole> roles = familyMemberRoleRepository
                .findAllByFamilyMemberIdIn(familyMembers.stream().map(FamilyMember::getId).toList());

        Map<UUID, Individual> individuals = new LinkedHashMap<>();
        individuals.put(proband.getId(), individualOfProband(proband));
        for (FamilyMember fm : familyMembers) {
            individuals.put(fm.getId(), individualOfFamilyMember(fm));
        }

        Map<UUID, Set<UUID>> parentsOf = buildParentEdges(probandId, roles, parentChildRows);
        List<SpouseEdge> spouseEdges = buildSpouseEdges(roles, partnerRows);

        assignGenerations(individuals, parentsOf, spouseEdges);
        assignExportIdsAndLabels(individuals);

        Map<UUID, List<UUID>> fullSiblings = computeFullSiblings(individuals.keySet(), parentsOf);
        Map<UUID, Map<UUID, List<UUID>>> halfSiblings = computeHalfSiblings(individuals.keySet(), parentsOf, fullSiblings);
        Map<UUID, Map<UUID, List<UUID>>> childrenByCoParent = computeChildrenByCoParent(individuals.keySet(), parentsOf);
        Map<UUID, List<SpouseEdge>> spousesByIndividual = groupSpousesByIndividual(spouseEdges);

        return buildDocument(proband, individuals, parentsOf, fullSiblings, halfSiblings, childrenByCoParent, spousesByIndividual);
    }

    // -----------------------------------------------------------------------------------------
    // Graph construction
    // -----------------------------------------------------------------------------------------

    // relationship_type now lives in FAMILY_MEMBER_ROLE, keyed by scope (QP-Family-Relationship-
    // Persistence-Design.md, 2026-10-23). FamilyMemberService auto-creates an explicit
    // PARENT_CHILD_RELATIONSHIP/PARTNER_RELATIONSHIP row for every role except one: MOTHER/FATHER
    // relative to the proband specifically, which can never be an explicit row (the proband can
    // never be the child in that table - ParentChildRelationship.java's own invariant) - that one
    // case stays implicit here, derived straight from the role. Every other role either already has
    // an explicit row (read from `rows` below) or is one of the types that was never auto-created
    // (siblings, extended family) and only connects to the graph via a manually-created row.
    private Map<UUID, Set<UUID>> buildParentEdges(UUID probandId, List<FamilyMemberRole> roles,
                                                    List<ParentChildRelationship> rows) {
        Map<UUID, Set<UUID>> parentsOf = new HashMap<>();
        for (FamilyMemberRole role : roles) {
            if (!isScopedToProband(role, probandId)) {
                continue;
            }
            if (role.getRelationshipType() == RelationshipType.MOTHER || role.getRelationshipType() == RelationshipType.FATHER) {
                parentsOf.computeIfAbsent(probandId, k -> new HashSet<>()).add(role.getFamilyMember().getId());
            }
        }
        for (ParentChildRelationship row : rows) {
            UUID parentId = row.getParentPerson() != null ? row.getParentPerson().getId() : row.getParentFamilyMember().getId();
            UUID childId = row.getChildFamilyMember().getId();
            parentsOf.computeIfAbsent(childId, k -> new HashSet<>()).add(parentId);
        }
        return parentsOf;
    }

    private boolean isScopedToProband(FamilyMemberRole role, UUID probandId) {
        return role.getScopePerson() != null && role.getScopePerson().getId().equals(probandId);
    }

    private List<SpouseEdge> buildSpouseEdges(List<FamilyMemberRole> roles, List<PartnerRelationship> rows) {
        List<SpouseEdge> edges = new ArrayList<>();
        Set<UnorderedPair> covered = new HashSet<>();
        for (PartnerRelationship row : rows) {
            UUID a = row.getPersonAPerson() != null ? row.getPersonAPerson().getId() : row.getPersonAFamilyMember().getId();
            UUID b = row.getPersonBPerson() != null ? row.getPersonBPerson().getId() : row.getPersonBFamilyMember().getId();
            edges.add(SpouseEdge.fromRow(a, b, row));
            covered.add(new UnorderedPair(a, b));
        }
        // Defensive fallback only - FamilyMemberService always auto-creates an explicit
        // PARTNER_RELATIONSHIP row for a SPOUSE role (unlike MOTHER/FATHER, nothing blocks it
        // regardless of scope), so this should never actually need to fire for new data.
        for (FamilyMemberRole role : roles) {
            if (role.getRelationshipType() == RelationshipType.SPOUSE) {
                UUID scopeId = role.getScopePerson() != null ? role.getScopePerson().getId() : role.getScopeFamilyMember().getId();
                UUID memberId = role.getFamilyMember().getId();
                UnorderedPair pair = new UnorderedPair(scopeId, memberId);
                if (!covered.contains(pair)) {
                    edges.add(SpouseEdge.implicitDefault(scopeId, memberId));
                    covered.add(pair);
                }
            }
        }
        return edges;
    }

    // -----------------------------------------------------------------------------------------
    // Generation assignment: spouses form a "generation unit" (union-find), then generation per
    // unit is its longest path from a root (a unit with no known parent edge) via parent-child
    // edges - the only way to reproduce the reference sample's convention (a spouse who married in
    // takes their partner's generation even though their own parents are unknown; a root couple
    // with no known ancestors sits at generation 0 even though one partner's parents are unknown
    // too). Kahn's-algorithm style so a bad-data cycle can't cause infinite recursion - any node
    // still unresolved after the queue drains is defaulted to generation 0 rather than crashing.
    // -----------------------------------------------------------------------------------------

    private void assignGenerations(Map<UUID, Individual> individuals, Map<UUID, Set<UUID>> parentsOf,
                                    List<SpouseEdge> spouseEdges) {
        UnionFind unionFind = new UnionFind(individuals.keySet());
        for (SpouseEdge edge : spouseEdges) {
            unionFind.union(edge.a(), edge.b());
        }

        Map<UUID, Set<UUID>> unitParentUnits = new HashMap<>();
        Map<UUID, Integer> inDegree = new HashMap<>();
        for (UUID id : individuals.keySet()) {
            UUID unit = unionFind.find(id);
            unitParentUnits.putIfAbsent(unit, new HashSet<>());
            inDegree.putIfAbsent(unit, 0);
        }
        for (Map.Entry<UUID, Set<UUID>> entry : parentsOf.entrySet()) {
            UUID childUnit = unionFind.find(entry.getKey());
            for (UUID parentId : entry.getValue()) {
                UUID parentUnit = unionFind.find(parentId);
                if (!parentUnit.equals(childUnit) && unitParentUnits.get(childUnit).add(parentUnit)) {
                    inDegree.merge(childUnit, 1, Integer::sum);
                }
            }
        }

        Map<UUID, Integer> unitGeneration = new HashMap<>();
        ArrayDeque<UUID> queue = new ArrayDeque<>();
        for (UUID unit : unitParentUnits.keySet()) {
            if (inDegree.get(unit) == 0) {
                unitGeneration.put(unit, 0);
                queue.add(unit);
            }
        }
        Map<UUID, List<UUID>> childUnitsOf = new HashMap<>();
        for (Map.Entry<UUID, Set<UUID>> entry : unitParentUnits.entrySet()) {
            for (UUID parentUnit : entry.getValue()) {
                childUnitsOf.computeIfAbsent(parentUnit, k -> new ArrayList<>()).add(entry.getKey());
            }
        }
        Map<UUID, Integer> remainingInDegree = new HashMap<>(inDegree);
        while (!queue.isEmpty()) {
            UUID unit = queue.poll();
            int generation = unitGeneration.get(unit);
            for (UUID childUnit : childUnitsOf.getOrDefault(unit, List.of())) {
                unitGeneration.merge(childUnit, generation + 1, Math::max);
                int remaining = remainingInDegree.merge(childUnit, -1, Integer::sum);
                if (remaining == 0) {
                    queue.add(childUnit);
                }
            }
        }
        // Any unit left unresolved (a cycle in bad data) defaults to generation 0.
        for (UUID unit : unitParentUnits.keySet()) {
            unitGeneration.putIfAbsent(unit, 0);
        }

        for (Individual individual : individuals.values()) {
            individual.generation = unitGeneration.get(unionFind.find(individual.sourceId));
        }
    }

    private void assignExportIdsAndLabels(Map<UUID, Individual> individuals) {
        List<Individual> ordered = new ArrayList<>(individuals.values());
        ordered.sort(Comparator.comparingInt((Individual i) -> i.generation)
                .thenComparing(i -> i.birthDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(i -> i.createdAt, Comparator.nullsLast(Comparator.naturalOrder())));

        Map<Integer, Integer> sequenceByGeneration = new HashMap<>();
        int nextExportId = 1;
        for (Individual individual : ordered) {
            individual.exportId = nextExportId++;
            int sequence = sequenceByGeneration.merge(individual.generation, 1, Integer::sum);
            individual.individualLabel = toRomanNumeral(individual.generation + 1) + "-" + sequence;
        }
    }

    // -----------------------------------------------------------------------------------------
    // Sibling / co-parent grouping
    // -----------------------------------------------------------------------------------------

    private Map<UUID, List<UUID>> computeFullSiblings(Set<UUID> ids, Map<UUID, Set<UUID>> parentsOf) {
        Map<UUID, List<UUID>> result = new HashMap<>();
        for (UUID a : ids) {
            Set<UUID> parentsA = parentsOf.getOrDefault(a, Set.of());
            if (parentsA.isEmpty()) {
                continue;
            }
            for (UUID b : ids) {
                if (a.equals(b)) {
                    continue;
                }
                if (parentsA.equals(parentsOf.getOrDefault(b, Set.of()))) {
                    result.computeIfAbsent(a, k -> new ArrayList<>()).add(b);
                }
            }
        }
        return result;
    }

    private Map<UUID, Map<UUID, List<UUID>>> computeHalfSiblings(Set<UUID> ids, Map<UUID, Set<UUID>> parentsOf,
                                                                   Map<UUID, List<UUID>> fullSiblings) {
        Map<UUID, Map<UUID, List<UUID>>> result = new HashMap<>();
        for (UUID a : ids) {
            Set<UUID> parentsA = parentsOf.getOrDefault(a, Set.of());
            if (parentsA.isEmpty()) {
                continue;
            }
            Set<UUID> fullSiblingsOfA = new HashSet<>(fullSiblings.getOrDefault(a, List.of()));
            for (UUID b : ids) {
                if (a.equals(b) || fullSiblingsOfA.contains(b)) {
                    continue;
                }
                Set<UUID> parentsB = parentsOf.getOrDefault(b, Set.of());
                for (UUID sharedParent : parentsA) {
                    if (parentsB.contains(sharedParent)) {
                        result.computeIfAbsent(a, k -> new HashMap<>())
                                .computeIfAbsent(sharedParent, k -> new ArrayList<>())
                                .add(b);
                    }
                }
            }
        }
        return result;
    }

    // Keyed by co-parent id, matching the reference sample (family_of_10_newschema_V4.json) -
    // children{}/halfsiblings{} are both "list of X, grouped by the other relevant individual's
    // id". When a child's other parent isn't known, this groups them under the known parent's own
    // id as a "no identified co-parent" bucket - a convention of this implementation, not something
    // the reference sample demonstrates (every child there has two known parents).
    private Map<UUID, Map<UUID, List<UUID>>> computeChildrenByCoParent(Set<UUID> ids, Map<UUID, Set<UUID>> parentsOf) {
        Map<UUID, Map<UUID, List<UUID>>> result = new HashMap<>();
        for (Map.Entry<UUID, Set<UUID>> entry : parentsOf.entrySet()) {
            UUID child = entry.getKey();
            if (!ids.contains(child)) {
                continue;
            }
            List<UUID> parents = new ArrayList<>(entry.getValue());
            for (UUID parent : parents) {
                UUID coParent = parents.size() == 2
                        ? parents.get(parents.get(0).equals(parent) ? 1 : 0)
                        : parent; // no known co-parent - bucket under the known parent's own id
                result.computeIfAbsent(parent, k -> new HashMap<>())
                        .computeIfAbsent(coParent, k -> new ArrayList<>())
                        .add(child);
            }
        }
        return result;
    }

    private Map<UUID, List<SpouseEdge>> groupSpousesByIndividual(List<SpouseEdge> edges) {
        Map<UUID, List<SpouseEdge>> result = new HashMap<>();
        for (SpouseEdge edge : edges) {
            result.computeIfAbsent(edge.a(), k -> new ArrayList<>()).add(edge);
            result.computeIfAbsent(edge.b(), k -> new ArrayList<>()).add(edge);
        }
        return result;
    }

    // -----------------------------------------------------------------------------------------
    // matingLineCode derivation - PROVISIONAL, pending the user's mentor's decision (2026-10-14).
    // Isolated in this one method so swapping the rule later is a one-method change, not a
    // re-architecture. Current rule: consanguinity wins whenever consanguineous=true (the
    // clinically load-bearing fact for genetic risk), regardless of relationshipStatus;
    // relationshipStatus only drives the code when consanguineous is not true, so SEPARATED/
    // DIVORCED still surfaces in the rendered line style rather than always defaulting to
    // "matingLine". matingLineType itself (the direct-API field, not asked by the question set)
    // is honored as a last resort for callers who set it directly.
    // -----------------------------------------------------------------------------------------
    private String deriveMatingLineCode(SpouseEdge edge) {
        if (Boolean.TRUE.equals(edge.consanguineous())) {
            return "consanguineous";
        }
        if (edge.relationshipStatus() == RelationshipStatusType.SEPARATED) {
            return "separated";
        }
        if (edge.relationshipStatus() == RelationshipStatusType.DIVORCED) {
            return "divorced";
        }
        return switch (edge.matingLineType()) {
            case CONSANGUINEOUS -> "consanguineous";
            case SEPARATED -> "separated";
            case DIVORCED -> "divorced";
            case NO_LONGER_TOGETHER -> "noLongerTogether";
            case CASUAL_OR_UNKNOWN -> "casualOrUnknown";
            case MATING_LINE -> "matingLine";
        };
    }

    private String mapReproductiveStatus(ReproductiveStatusType status) {
        return switch (status) {
            case HAS_CHILDREN -> "hasChildren";
            case HAS_CHILDREN_NOT_SHOWN -> "hasChildrenNotShown";
            case NO_OFFSPRING -> "noOffspring";
            case INFERTILE -> "infertile";
            case NO_ISSUE_BY_CHOICE -> "noIssueByChoice";
            case PREGNANCY_ONLY -> "pregnancyOnly";
            case UNKNOWN -> "unknown";
        };
    }

    // -----------------------------------------------------------------------------------------
    // Document assembly
    // -----------------------------------------------------------------------------------------

    private JsonNode buildDocument(Person proband, Map<UUID, Individual> individuals, Map<UUID, Set<UUID>> parentsOf,
                                    Map<UUID, List<UUID>> fullSiblings, Map<UUID, Map<UUID, List<UUID>>> halfSiblings,
                                    Map<UUID, Map<UUID, List<UUID>>> childrenByCoParent,
                                    Map<UUID, List<SpouseEdge>> spousesByIndividual) {
        ObjectNode document = objectMapper.createObjectNode();
        document.put("schemaVersion", "4.0.0");
        document.put("schemaCompliance", "PSWG / Bennett 2022 + 2025 correction — pedigree-schema");
        String probandName = fullName(proband.getFirstName(), proband.getLastName());
        document.put("title", probandName.isBlank() ? "Pedigree" : probandName + " Pedigree");
        document.put("createdDate", LocalDateTime.now().toString());

        Map<UUID, Integer> diseaseRefIds = new LinkedHashMap<>();
        document.set("diseases", buildDiseaseCatalog(individuals, diseaseRefIds));

        ObjectNode individualsNode = objectMapper.createObjectNode();
        for (Individual individual : individuals.values()) {
            individualsNode.set(String.valueOf(individual.exportId),
                    buildIndividual(individual, individuals, parentsOf, fullSiblings, halfSiblings,
                            childrenByCoParent, spousesByIndividual, diseaseRefIds));
        }
        document.set("individuals", individualsNode);
        return document;
    }

    // Document-level rendering legend (pedigree_schema.json's top-level diseases[]) - distinct from
    // each individual's own conditions[]. Every distinct DISEASE_CATALOG entry referenced anywhere
    // in this pedigree gets one entry here, up to the schema's hard cap of 4, assigned a quadrant
    // clockwise in the order first encountered (individuals in generation/export-id order). The
    // schema's 5th possible quadrant value, centerDot, is never assigned - no rule exists yet for
    // when a disease should render as a center dot instead of a quadrant (QP-Pedigree-Export.md).
    private static final String[] DISEASE_QUADRANTS = {"upperLeft", "upperRight", "lowerRight", "lowerLeft"};

    private ArrayNode buildDiseaseCatalog(Map<UUID, Individual> individuals, Map<UUID, Integer> diseaseRefIds) {
        ArrayNode diseasesNode = objectMapper.createArrayNode();
        for (Individual individual : individuals.values()) {
            for (Disease disease : individual.diseases) {
                DiseaseCatalog catalog = disease.getDiseaseCatalog();
                if (diseaseRefIds.containsKey(catalog.getId()) || diseaseRefIds.size() >= 4) {
                    continue;
                }
                int refId = diseaseRefIds.size() + 1;
                diseaseRefIds.put(catalog.getId(), refId);
                ObjectNode diseaseNode = objectMapper.createObjectNode();
                diseaseNode.put("id", refId);
                diseaseNode.put("name", catalog.getName());
                diseaseNode.put("abbreviation", catalog.getAbbreviation() != null ? catalog.getAbbreviation() : catalog.getName());
                diseaseNode.put("color", catalog.getDefaultColor() != null ? catalog.getDefaultColor() : "#808080");
                diseaseNode.put("quadrant", DISEASE_QUADRANTS[refId - 1]);
                diseasesNode.add(diseaseNode);
            }
        }
        return diseasesNode;
    }

    private ObjectNode buildIndividual(Individual individual, Map<UUID, Individual> individuals,
                                        Map<UUID, Set<UUID>> parentsOf, Map<UUID, List<UUID>> fullSiblings,
                                        Map<UUID, Map<UUID, List<UUID>>> halfSiblings,
                                        Map<UUID, Map<UUID, List<UUID>>> childrenByCoParent,
                                        Map<UUID, List<SpouseEdge>> spousesByIndividual,
                                        Map<UUID, Integer> diseaseRefIds) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", individual.exportId);
        String name = fullName(individual.firstName, individual.lastName);
        if (!name.isBlank()) {
            node.put("name", name);
        }
        node.put("individualLabel", individual.individualLabel);
        node.put("generation", individual.generation);
        node.put("external", false);
        node.put("donor", false);
        node.put("notExamined", false);

        node.put("gender", mapGender(individual.gender));
        node.put("sex", mapSex(individual.sex));

        node.put("lifeStatus", individual.deceased == null ? "unknown" : individual.deceased ? "deceased" : "alive");
        node.set("birthYear", individual.birthDate == null ? objectMapper.nullNode() : objectMapper.valueToTree(individual.birthDate.getYear()));
        node.set("age", objectMapper.valueToTree(resolveAge(individual)));
        node.set("deathYear", objectMapper.valueToTree(resolveDeathYear(individual)));

        node.put("role", individual.proband ? "proband" : "none");

        ArrayNode conditions = objectMapper.createArrayNode();
        for (Disease disease : individual.diseases) {
            ObjectNode condition = objectMapper.createObjectNode();
            Integer diseaseRefId = diseaseRefIds.get(disease.getDiseaseCatalog().getId());
            if (diseaseRefId != null) {
                condition.put("diseaseRefId", diseaseRefId);
            } else {
                condition.set("diseaseRefId", objectMapper.nullNode());
            }
            ObjectNode code = objectMapper.createObjectNode();
            code.put("system", mapCodeSystem(disease.getCodeSystem()));
            code.put("id", disease.getCodeId());
            if (disease.getCodeLabel() != null) {
                code.put("label", disease.getCodeLabel());
            }
            condition.set("code", code);
            condition.set("ageAtDiagnosis", disease.getAgeAtDiagnosis() == null
                    ? objectMapper.nullNode() : objectMapper.valueToTree(disease.getAgeAtDiagnosis()));
            condition.set("laterality", disease.getLaterality() == null
                    ? objectMapper.nullNode() : objectMapper.valueToTree(mapLaterality(disease.getLaterality())));
            condition.put("status", mapDiseaseStatus(disease.getStatus()));
            conditions.add(condition);
        }
        node.set("conditions", conditions);

        // affected/affectedMultiple derived from conditions[].length. When unaffected, carrierHet is
        // derived from genetic-test results: a pathogenic/likely-pathogenic MUTATION with no matching
        // condition implies heterozygous carrier status. obligateCarrier (requires pedigree-structural
        // inheritance inference - who else in the pedigree is affected/related) and a distinct
        // asymptomaticCarrier (would overlap with carrierHet without a separate signal to tell them
        // apart) are deliberately NOT derived here - no rule exists yet for either (§6.2).
        boolean hasPathogenicMutation = individual.mutations.stream()
                .anyMatch(mutation -> mutation.getResult() == MutationResultType.PATHOGENIC
                        || mutation.getResult() == MutationResultType.LIKELY_PATHOGENIC);
        ArrayNode affectedAndCarrier = objectMapper.createArrayNode();
        if (!conditions.isEmpty()) {
            affectedAndCarrier.add(conditions.size() == 1 ? "affected" : "affectedMultiple");
        } else if (hasPathogenicMutation) {
            affectedAndCarrier.add("carrierHet");
        } else {
            affectedAndCarrier.add("unaffected");
        }
        node.set("affectedAndCarrier", affectedAndCarrier);

        ArrayNode annotations = objectMapper.createArrayNode();
        if (individual.sex == SexAssignedAtBirthType.MALE && individual.gender == GenderIdentityType.WOMAN) {
            annotations.add("amabAnnotation");
        } else if (individual.sex == SexAssignedAtBirthType.FEMALE && individual.gender == GenderIdentityType.MAN) {
            annotations.add("afabAnnotation");
        }
        if (Boolean.TRUE.equals(individual.intersex)) {
            annotations.add("intersexAnnotation");
        }
        node.set("individualAnnotation", annotations);

        if (individual.genderTransition != null) {
            ObjectNode genderTransition = objectMapper.createObjectNode();
            genderTransition.put("fromGender", mapGender(individual.genderTransition.getFromGender()));
            genderTransition.put("toGender", mapGender(individual.genderTransition.getToGender()));
            genderTransition.set("transitionYear", individual.genderTransition.getTransitionYear() == null
                    ? objectMapper.nullNode() : objectMapper.valueToTree(individual.genderTransition.getTransitionYear()));
            node.set("genderTransition", genderTransition);
        }

        ObjectNode ancestry = objectMapper.createObjectNode();
        ArrayNode ethnicityArray = objectMapper.createArrayNode();
        if (individual.ethnicity != null) {
            for (String e : individual.ethnicity) {
                ethnicityArray.add(e);
            }
        }
        ancestry.set("selfReportedEthnicity", ethnicityArray);
        ArrayNode countryArray = objectMapper.createArrayNode();
        if (individual.countryOfOrigin != null) {
            for (String code : individual.countryOfOrigin) {
                countryArray.add(code);
            }
        }
        ancestry.set("countryOfOrigin", countryArray);
        ancestry.set("inferredAncestry", objectMapper.nullNode());
        node.set("ancestry", ancestry);

        ArrayNode geneticTests = objectMapper.createArrayNode();
        for (Mutation mutation : individual.mutations) {
            ObjectNode test = objectMapper.createObjectNode();
            test.put("gene", mutation.getGene());
            test.put("result", mapMutationResult(mutation.getResult()));
            test.set("variant", mutation.getVariant() == null ? objectMapper.nullNode() : objectMapper.valueToTree(mutation.getVariant()));
            test.set("testType", mutation.getTestType() == null
                    ? objectMapper.nullNode() : objectMapper.valueToTree(mapTestType(mutation.getTestType())));
            geneticTests.add(test);
        }
        node.set("geneticTests", geneticTests);
        node.set("extensions", objectMapper.createObjectNode());

        ArrayNode parents = objectMapper.createArrayNode();
        for (UUID parentId : parentsOf.getOrDefault(individual.sourceId, Set.of())) {
            Individual parent = individuals.get(parentId);
            if (parent != null) {
                parents.add(parent.exportId);
            }
        }
        node.set("parents", parents);

        ObjectNode spouses = objectMapper.createObjectNode();
        for (SpouseEdge edge : spousesByIndividual.getOrDefault(individual.sourceId, List.of())) {
            UUID otherId = edge.a().equals(individual.sourceId) ? edge.b() : edge.a();
            Individual other = individuals.get(otherId);
            if (other == null) {
                continue;
            }
            ObjectNode spouseEntry = objectMapper.createObjectNode();
            String matingLineCode = deriveMatingLineCode(edge);
            spouseEntry.put("matingLineCode", matingLineCode);
            spouseEntry.put("reproductiveStatus", mapReproductiveStatus(edge.reproductiveStatus()));
            if (edge.reproductiveStatus() == ReproductiveStatusType.HAS_CHILDREN_NOT_SHOWN && edge.unshownChildrenCount() != null) {
                spouseEntry.put("unshownChildrenCount", edge.unshownChildrenCount());
            }
            if (matingLineCode.equals("consanguineous") && edge.consanguinityDegree() != null && !edge.consanguinityDegree().isBlank()) {
                spouseEntry.put("consanguinityDegree", edge.consanguinityDegree());
            }
            if ((matingLineCode.equals("separated") || matingLineCode.equals("divorced") || matingLineCode.equals("noLongerTogether"))
                    && edge.endedYear() != null) {
                spouseEntry.put("endedYear", edge.endedYear());
            }
            spouses.set(String.valueOf(other.exportId), spouseEntry);
        }
        node.set("spouses", spouses);

        ObjectNode children = objectMapper.createObjectNode();
        for (Map.Entry<UUID, List<UUID>> entry : childrenByCoParent.getOrDefault(individual.sourceId, Map.of()).entrySet()) {
            Individual coParent = individuals.get(entry.getKey());
            ArrayNode childIds = objectMapper.createArrayNode();
            for (UUID childId : entry.getValue()) {
                Individual child = individuals.get(childId);
                if (child != null) {
                    childIds.add(child.exportId);
                }
            }
            if (coParent != null) {
                children.set(String.valueOf(coParent.exportId), childIds);
            }
        }
        node.set("children", children);

        ArrayNode siblings = objectMapper.createArrayNode();
        for (UUID siblingId : fullSiblings.getOrDefault(individual.sourceId, List.of())) {
            Individual sibling = individuals.get(siblingId);
            if (sibling != null) {
                siblings.add(sibling.exportId);
            }
        }
        node.set("siblings", siblings);

        ObjectNode halfSiblingsNode = objectMapper.createObjectNode();
        for (Map.Entry<UUID, List<UUID>> entry : halfSiblings.getOrDefault(individual.sourceId, Map.of()).entrySet()) {
            Individual sharedParent = individuals.get(entry.getKey());
            ArrayNode halfSiblingIds = objectMapper.createArrayNode();
            for (UUID siblingId : entry.getValue()) {
                Individual sibling = individuals.get(siblingId);
                if (sibling != null) {
                    halfSiblingIds.add(sibling.exportId);
                }
            }
            if (sharedParent != null) {
                halfSiblingsNode.set(String.valueOf(sharedParent.exportId), halfSiblingIds);
            }
        }
        node.set("halfsiblings", halfSiblingsNode);

        if (individual.twinshipMembership != null) {
            ObjectNode twinship = objectMapper.createObjectNode();
            twinship.put("type", mapTwinType(individual.twinshipMembership.getTwinship().getTwinType()));
            ArrayNode coTwins = objectMapper.createArrayNode();
            for (TwinshipMember member : twinshipMemberRepository.findAllByTwinshipId(individual.twinshipMembership.getTwinship().getId())) {
                UUID memberId = member.getPerson() != null ? member.getPerson().getId() : member.getFamilyMember().getId();
                if (memberId.equals(individual.sourceId)) {
                    continue;
                }
                Individual coTwin = individuals.get(memberId);
                if (coTwin != null) {
                    coTwins.add(coTwin.exportId);
                }
            }
            twinship.set("coTwins", coTwins);
            node.set("twinship", twinship);
        } else {
            node.set("twinship", objectMapper.nullNode());
        }

        if (individual.adoption != null) {
            ObjectNode adoption = objectMapper.createObjectNode();
            adoption.put("type", mapAdoptionType(individual.adoption.getAdoptionType()));
            ArrayNode adoptiveParents = objectMapper.createArrayNode();
            for (AdoptionParent parent : individual.adoptionParents) {
                UUID parentId = parent.getParentPerson() != null ? parent.getParentPerson().getId() : parent.getParentFamilyMember().getId();
                Individual parentIndividual = individuals.get(parentId);
                if (parentIndividual != null) {
                    adoptiveParents.add(parentIndividual.exportId);
                }
            }
            if (!adoptiveParents.isEmpty()) {
                adoption.set("adoptiveParents", adoptiveParents);
            }
            // Biological parents are never stored on ADOPTION_PARENT - derived from the same
            // parentsOf graph used for everyone's own "parents" field (QP-Pedigree-Schema-Design.md
            // §2.7's final-review simplification).
            ArrayNode biologicalParents = objectMapper.createArrayNode();
            for (UUID parentId : parentsOf.getOrDefault(individual.sourceId, Set.of())) {
                Individual parent = individuals.get(parentId);
                if (parent != null) {
                    biologicalParents.add(parent.exportId);
                }
            }
            if (!biologicalParents.isEmpty()) {
                adoption.set("biologicalParents", biologicalParents);
            }
            node.set("adoption", adoption);
        } else {
            node.set("adoption", objectMapper.nullNode());
        }

        if (individual.pregnancyOutcome != null) {
            ObjectNode pregnancyOutcome = objectMapper.createObjectNode();
            pregnancyOutcome.put("type", mapPregnancyOutcomeType(individual.pregnancyOutcome.getOutcomeType()));
            if (individual.pregnancyOutcome.getGestationalWeeks() != null) {
                pregnancyOutcome.put("gestationalWeeks", individual.pregnancyOutcome.getGestationalWeeks());
            }
            if (individual.pregnancyOutcome.getLmpDate() != null) {
                pregnancyOutcome.put("lmpDate", individual.pregnancyOutcome.getLmpDate().toString());
            }
            node.set("pregnancyOutcome", pregnancyOutcome);
        } else {
            node.set("pregnancyOutcome", objectMapper.nullNode());
        }

        if (individual.assistedReproduction != null) {
            ObjectNode assistedReproduction = objectMapper.createObjectNode();
            assistedReproduction.put("type", mapAssistedReproductionType(individual.assistedReproduction.getReproductionType()));
            if (individual.assistedReproduction.getAnonymous() != null) {
                assistedReproduction.put("anonymous", individual.assistedReproduction.getAnonymous());
            }
            ArrayNode intendedParents = objectMapper.createArrayNode();
            ArrayNode donatedTo = objectMapper.createArrayNode();
            ArrayNode carriedFor = objectMapper.createArrayNode();
            for (AssistedReproductionParticipant participant : individual.assistedReproductionParticipants) {
                UUID participantId = participant.getParticipantPerson() != null
                        ? participant.getParticipantPerson().getId() : participant.getParticipantFamilyMember().getId();
                Individual participantIndividual = individuals.get(participantId);
                if (participantIndividual == null) {
                    continue;
                }
                switch (participant.getParticipantRole()) {
                    case EGG_DONOR -> assistedReproduction.put("eggDonorId", participantIndividual.exportId);
                    case SPERM_DONOR -> assistedReproduction.put("spermDonorId", participantIndividual.exportId);
                    case GESTATIONAL_CARRIER -> assistedReproduction.put("gestationalCarrierId", participantIndividual.exportId);
                    case INTENDED_PARENT -> intendedParents.add(participantIndividual.exportId);
                    case DONATED_TO -> donatedTo.add(participantIndividual.exportId);
                    case CARRIED_FOR -> carriedFor.add(participantIndividual.exportId);
                }
            }
            if (!intendedParents.isEmpty()) {
                assistedReproduction.set("intendedParents", intendedParents);
            }
            if (!donatedTo.isEmpty()) {
                assistedReproduction.set("donatedTo", donatedTo);
            }
            if (!carriedFor.isEmpty()) {
                assistedReproduction.set("carriedFor", carriedFor);
            }
            node.set("assistedReproduction", assistedReproduction);
        } else {
            node.set("assistedReproduction", objectMapper.nullNode());
        }

        return node;
    }

    private String mapCodeSystem(com.questionnaire.questionnaire_platform.entity.DiseaseCodeSystemType codeSystem) {
        return switch (codeSystem) {
            case ICD_10 -> "ICD-10";
            case ICD_O_3 -> "ICD-O-3";
            case OMIM -> "OMIM";
            case HPO -> "HPO";
            case SNOMED_CT -> "SNOMED-CT";
            case CUSTOM -> "custom";
        };
    }

    private String mapLaterality(com.questionnaire.questionnaire_platform.entity.DiseaseLateralityType laterality) {
        return switch (laterality) {
            case LEFT -> "left";
            case RIGHT -> "right";
            case BILATERAL -> "bilateral";
        };
    }

    private String mapDiseaseStatus(com.questionnaire.questionnaire_platform.entity.DiseaseStatusType status) {
        return switch (status) {
            case CONFIRMED -> "confirmed";
            case SUSPECTED -> "suspected";
            case REPORTED -> "reported";
        };
    }

    private String mapMutationResult(com.questionnaire.questionnaire_platform.entity.MutationResultType result) {
        return switch (result) {
            case PATHOGENIC -> "pathogenic";
            case LIKELY_PATHOGENIC -> "likely_pathogenic";
            case VUS -> "vus";
            case LIKELY_BENIGN -> "likely_benign";
            case BENIGN -> "benign";
            case NEGATIVE -> "negative";
            case PENDING -> "pending";
        };
    }

    private String mapTestType(com.questionnaire.questionnaire_platform.entity.GeneticTestType testType) {
        return switch (testType) {
            case SINGLE_GENE -> "single_gene";
            case PANEL -> "panel";
            case WES -> "wes";
            case WGS -> "wgs";
            case TARGETED -> "targeted";
        };
    }

    private String mapTwinType(com.questionnaire.questionnaire_platform.entity.TwinType twinType) {
        return switch (twinType) {
            case MONOZYGOTIC -> "twinsMonozygotic";
            case DIZYGOTIC -> "twinsDizygotic";
            case ZYGOSITY_UNKNOWN -> "twinsZygosityUnknown";
            case HIGHER_ORDER_MULTIPLE -> "tripletsHigherOrder";
            case CONJOINED -> "conjoinedTwins";
        };
    }

    private String mapAdoptionType(com.questionnaire.questionnaire_platform.entity.AdoptionType adoptionType) {
        return switch (adoptionType) {
            case ADOPTED_IN -> "adoptedIn";
            case ADOPTED_OUT -> "adoptedOut";
        };
    }

    private String mapPregnancyOutcomeType(com.questionnaire.questionnaire_platform.entity.PregnancyOutcomeType outcomeType) {
        return switch (outcomeType) {
            case CURRENT_PREGNANCY -> "currentPregnancy";
            case AFFECTED_PREGNANCY -> "affectedPregnancy";
            case PREGNANCY_LMP -> "pregnancyLmp";
            case SAB -> "sab";
            case SAB_AFFECTED -> "sabAffected";
            case TOP -> "top";
            case TOP_AFFECTED -> "topAffected";
            case ECTOPIC -> "ectopic";
            case STILLBIRTH -> "stillbirth";
        };
    }

    private String mapAssistedReproductionType(com.questionnaire.questionnaire_platform.entity.AssistedReproductionType reproductionType) {
        return switch (reproductionType) {
            case DONOR -> "donor";
            case SPERM_DONOR -> "spermDonor";
            case EGG_DONOR -> "eggDonor";
            case GESTATIONAL_CARRIER -> "gestationalCarrier";
            case SURROGATE -> "surrogate";
            case INTENDED_PARENT -> "intendedParent";
            case IVF -> "ivf";
            case IUI -> "iui";
            case ICSI -> "icsi";
            case EMBRYO_ADOPTION -> "embryoAdoption";
            case ANONYMOUS_DONOR -> "anonymousDonor";
        };
    }

    private Integer resolveAge(Individual individual) {
        if (Boolean.TRUE.equals(individual.deceased)) {
            return individual.ageAtDeath;
        }
        if (individual.birthDate != null) {
            return Period.between(individual.birthDate.toLocalDate(), LocalDate.now()).getYears();
        }
        return individual.age;
    }

    private Integer resolveDeathYear(Individual individual) {
        if (!Boolean.TRUE.equals(individual.deceased)) {
            return null;
        }
        if (individual.deceasedDateTime != null) {
            return individual.deceasedDateTime.getYear();
        }
        if (individual.birthDate != null && individual.ageAtDeath != null) {
            return individual.birthDate.getYear() + individual.ageAtDeath;
        }
        return null;
    }

    private int mapGender(GenderIdentityType gender) {
        if (gender == null) {
            return 4; // unknown
        }
        return switch (gender) {
            case MAN -> 1;
            case WOMAN -> 2;
            case NONBINARY -> 3;
            case UNKNOWN -> 4;
        };
    }

    private int mapSex(SexAssignedAtBirthType sex) {
        if (sex == null) {
            return 0; // unknown
        }
        return switch (sex) {
            case UNKNOWN -> 0;
            case MALE -> 1;
            case FEMALE -> 2;
        };
    }

    private String fullName(String firstName, String lastName) {
        StringBuilder builder = new StringBuilder();
        if (firstName != null && !firstName.isBlank()) {
            builder.append(firstName.trim());
        }
        if (lastName != null && !lastName.isBlank()) {
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(lastName.trim());
        }
        return builder.toString();
    }

    private static final String[] ROMAN_SYMBOLS = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
    private static final int[] ROMAN_VALUES = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};

    private String toRomanNumeral(int number) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < ROMAN_VALUES.length && number > 0; i++) {
            while (number >= ROMAN_VALUES[i]) {
                number -= ROMAN_VALUES[i];
                builder.append(ROMAN_SYMBOLS[i]);
            }
        }
        return builder.toString();
    }

    private Individual individualOfProband(Person person) {
        Individual individual = new Individual();
        individual.sourceId = person.getId();
        individual.proband = true;
        individual.firstName = person.getFirstName();
        individual.lastName = person.getLastName();
        individual.sex = person.getSexAssignedAtBirth();
        individual.gender = person.getGenderIdentity();
        individual.intersex = person.getIsIntersex();
        individual.deceased = person.getDeceased();
        individual.birthDate = person.getBirthDate();
        individual.deceasedDateTime = person.getDeceasedDateTime();
        individual.age = person.getAge();
        individual.ageAtDeath = readAgeAtDeath(person.getAdditionalFacts());
        individual.ethnicity = person.getEthnicity();
        individual.countryOfOrigin = person.getCountryOfOrigin();
        individual.createdAt = person.getCreatedAt();
        populatePedigreeExtensions(individual, person.getId(), null);
        return individual;
    }

    private Individual individualOfFamilyMember(FamilyMember familyMember) {
        Individual individual = new Individual();
        individual.sourceId = familyMember.getId();
        individual.proband = false;
        individual.firstName = familyMember.getFirstName();
        individual.lastName = familyMember.getLastName();
        individual.sex = familyMember.getSexAssignedAtBirth();
        individual.gender = familyMember.getGenderIdentity();
        individual.intersex = familyMember.getIsIntersex();
        individual.deceased = familyMember.getDeceased();
        individual.birthDate = familyMember.getBirthDate();
        individual.deceasedDateTime = familyMember.getDeceasedDateTime();
        individual.age = familyMember.getAge();
        individual.ageAtDeath = readAgeAtDeath(familyMember.getAdditionalFacts());
        individual.ethnicity = familyMember.getEthnicity();
        individual.countryOfOrigin = familyMember.getCountryOfOrigin();
        individual.createdAt = familyMember.getCreatedAt();
        populatePedigreeExtensions(individual, null, familyMember.getId());
        return individual;
    }

    // Exactly one of personId/familyMemberId is non-null, the same XOR convention every
    // pedigree-extension table uses. pregnancyOutcome never applies to the proband (a pregnancy
    // loss/current pregnancy always gets its own FAMILY_MEMBER row, never the proband's own) - left
    // null in that branch, matching PREGNANCY_OUTCOME's own NOT NULL family_member_id column.
    private void populatePedigreeExtensions(Individual individual, UUID personId, UUID familyMemberId) {
        individual.diseases = personId != null
                ? diseaseRepository.findAllByPersonId(personId) : diseaseRepository.findAllByFamilyMemberId(familyMemberId);
        individual.mutations = personId != null
                ? mutationRepository.findAllByPersonId(personId) : mutationRepository.findAllByFamilyMemberId(familyMemberId);
        individual.genderTransition = (personId != null
                ? genderTransitionRepository.findByPersonId(personId) : genderTransitionRepository.findByFamilyMemberId(familyMemberId))
                .orElse(null);
        individual.twinshipMembership = (personId != null
                ? twinshipMemberRepository.findByPersonId(personId) : twinshipMemberRepository.findByFamilyMemberId(familyMemberId))
                .orElse(null);
        individual.adoption = (personId != null
                ? adoptionRepository.findByIndividualPersonId(personId) : adoptionRepository.findByIndividualFamilyMemberId(familyMemberId))
                .orElse(null);
        individual.adoptionParents = individual.adoption != null
                ? adoptionParentRepository.findAllByAdoptionId(individual.adoption.getId()) : List.of();
        individual.pregnancyOutcome = familyMemberId != null
                ? pregnancyOutcomeRepository.findByFamilyMemberId(familyMemberId).orElse(null) : null;
        individual.assistedReproduction = (personId != null
                ? assistedReproductionRepository.findByIndividualPersonId(personId)
                : assistedReproductionRepository.findByIndividualFamilyMemberId(familyMemberId))
                .orElse(null);
        individual.assistedReproductionParticipants = individual.assistedReproduction != null
                ? assistedReproductionParticipantRepository.findAllByAssistedReproductionId(individual.assistedReproduction.getId())
                : List.of();
    }

    // age_at_death has no dedicated column (PersonAnswerWriter/FamilyMemberAnswerWriter - neither
    // writer has a case for it) - it lands in additional_facts like any other unrecognized
    // answer_key, so reading it back means parsing that JSONB blob, not a simple getter.
    private Integer readAgeAtDeath(String additionalFactsJson) {
        JsonNode facts = jsonColumnMapper.read(additionalFactsJson);
        if (facts == null || !facts.isObject() || !facts.has("age_at_death")) {
            return null;
        }
        JsonNode value = facts.get("age_at_death");
        return value.isNull() ? null : value.asInt();
    }

    // -----------------------------------------------------------------------------------------
    // Internal graph types
    // -----------------------------------------------------------------------------------------

    private static final class Individual {
        UUID sourceId;
        boolean proband;
        String firstName;
        String lastName;
        SexAssignedAtBirthType sex;
        GenderIdentityType gender;
        Boolean intersex;
        Boolean deceased;
        LocalDateTime birthDate;
        LocalDateTime deceasedDateTime;
        Integer age;
        Integer ageAtDeath;
        String[] ethnicity;
        String[] countryOfOrigin;
        LocalDateTime createdAt;
        int generation;
        int exportId;
        String individualLabel;

        List<Disease> diseases = List.of();
        List<Mutation> mutations = List.of();
        GenderTransition genderTransition;
        TwinshipMember twinshipMembership;
        Adoption adoption;
        List<AdoptionParent> adoptionParents = List.of();
        PregnancyOutcome pregnancyOutcome;
        AssistedReproduction assistedReproduction;
        List<AssistedReproductionParticipant> assistedReproductionParticipants = List.of();
    }

    private record SpouseEdge(UUID a, UUID b, MatingLineType matingLineType, ReproductiveStatusType reproductiveStatus,
                               RelationshipStatusType relationshipStatus, Boolean consanguineous,
                               String consanguinityDegree, Integer unshownChildrenCount, Integer endedYear) {

        static SpouseEdge fromRow(UUID a, UUID b, PartnerRelationship row) {
            return new SpouseEdge(a, b, row.getMatingLineType(), row.getReproductiveStatus(), row.getRelationshipStatus(),
                    row.getConsanguineous(), row.getConsanguinityDegree(), row.getUnshownChildrenCount(), row.getEndedYear());
        }

        static SpouseEdge implicitDefault(UUID a, UUID b) {
            return new SpouseEdge(a, b, MatingLineType.MATING_LINE, ReproductiveStatusType.UNKNOWN, null, null, null, null, null);
        }
    }

    private record UnorderedPair(UUID x, UUID y) {
        UnorderedPair {
            if (x.compareTo(y) > 0) {
                UUID tmp = x;
                x = y;
                y = tmp;
            }
        }
    }

    private static final class UnionFind {
        private final Map<UUID, UUID> parent = new HashMap<>();

        UnionFind(Set<UUID> ids) {
            for (UUID id : ids) {
                parent.put(id, id);
            }
        }

        UUID find(UUID id) {
            UUID root = id;
            while (!parent.get(root).equals(root)) {
                root = parent.get(root);
            }
            UUID current = id;
            while (!current.equals(root)) {
                UUID next = parent.get(current);
                parent.put(current, root);
                current = next;
            }
            return root;
        }

        void union(UUID a, UUID b) {
            UUID rootA = find(a);
            UUID rootB = find(b);
            if (!rootA.equals(rootB)) {
                parent.put(rootA, rootB);
            }
        }
    }
}
