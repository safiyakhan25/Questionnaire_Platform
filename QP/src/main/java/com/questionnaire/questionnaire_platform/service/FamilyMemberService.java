package com.questionnaire.questionnaire_platform.service;

import com.questionnaire.questionnaire_platform.dto.FamilyMemberCreateRequest;
import com.questionnaire.questionnaire_platform.dto.FamilyMemberResponse;
import com.questionnaire.questionnaire_platform.dto.FamilyMemberRoleView;
import com.questionnaire.questionnaire_platform.dto.RelativeCountReconciliation;
import com.questionnaire.questionnaire_platform.entity.FamilyMember;
import com.questionnaire.questionnaire_platform.entity.FamilyMemberRole;
import com.questionnaire.questionnaire_platform.entity.MatingLineType;
import com.questionnaire.questionnaire_platform.entity.ParentChildRelationship;
import com.questionnaire.questionnaire_platform.entity.PartnerRelationship;
import com.questionnaire.questionnaire_platform.entity.Person;
import com.questionnaire.questionnaire_platform.entity.QuestionnaireSession;
import com.questionnaire.questionnaire_platform.entity.RelationshipType;
import com.questionnaire.questionnaire_platform.entity.ReproductiveStatusType;
import com.questionnaire.questionnaire_platform.entity.SessionStatus;
import com.questionnaire.questionnaire_platform.exception.DuplicateResourceException;
import com.questionnaire.questionnaire_platform.exception.ResourceInUseException;
import com.questionnaire.questionnaire_platform.exception.ResourceNotFoundException;
import com.questionnaire.questionnaire_platform.repository.FamilyMemberRepository;
import com.questionnaire.questionnaire_platform.repository.FamilyMemberRoleRepository;
import com.questionnaire.questionnaire_platform.repository.ParentChildRelationshipRepository;
import com.questionnaire.questionnaire_platform.repository.PartnerRelationshipRepository;
import com.questionnaire.questionnaire_platform.repository.QuestionResponseRepository;
import com.questionnaire.questionnaire_platform.repository.QuestionnaireSessionRepository;
import com.questionnaire.questionnaire_platform.util.JsonColumnMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// A family member is always added/removed in the context of a session (mid-assessment, by the
// current subject reporting a relative) - every operation here is scoped by sessionId, resolving
// to that session's own proband, the same way AssessmentService scopes answers by sessionId.
//
// Recursive adding (QP-Family-Relationship-Persistence-Design.md, 2026-10-23): any family member
// can be the "scope" (anchor) for adding further relatives, not only the proband. relationship_type
// is no longer a column on FAMILY_MEMBER - it moved to FAMILY_MEMBER_ROLE, keyed by scope, because
// the same real person can now legitimately hold more than one true relational label (SPOUSE
// relative to one scope, MOTHER relative to another).
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FamilyMemberService {

    // FR-13.1: relationship types singular per scope - blocked outright, a second one under the
    // same anchor is always a duplicate. Only MOTHER/FATHER are named in the SRS; everything else
    // (including GRANDFATHER/GRANDMOTHER/SPOUSE) is left unenforced rather than guessed at.
    private static final Set<RelationshipType> SINGULAR_RELATIONSHIP_TYPES =
            EnumSet.of(RelationshipType.MOTHER, RelationshipType.FATHER);

    // Types that imply "the new family member is the scope's child" - the scope is always a valid
    // parent (proband or family member), so the edge can always be auto-created.
    private static final Set<RelationshipType> CHILD_OF_SCOPE_TYPES =
            EnumSet.of(RelationshipType.SON, RelationshipType.DAUGHTER,
                    RelationshipType.ADOPTED_SON, RelationshipType.ADOPTED_DAUGHTER);

    // Types that imply "the new family member is the scope's parent" - only auto-createable as an
    // explicit edge when the scope is itself a family member, never when the scope is the proband
    // (ParentChildRelationship's own invariant: the proband can never be the child there - that
    // edge stays implicit, derived at export time, exactly as it always has been).
    private static final Set<RelationshipType> PARENT_OF_SCOPE_TYPES =
            EnumSet.of(RelationshipType.MOTHER, RelationshipType.FATHER);

    // FR-13.2/13.3: relationship types reconciled against a declared count instead of blocked.
    // Grouping and additional_facts key names match the SRS's own wording exactly ("siblings,
    // children, aunts/uncles, cousins, nieces, nephews").
    private static final Map<String, List<RelationshipType>> RECONCILED_GROUPS = new LinkedHashMap<>();

    static {
        RECONCILED_GROUPS.put("sibling_count", List.of(
                RelationshipType.BROTHER, RelationshipType.SISTER, RelationshipType.HALF_BROTHER, RelationshipType.HALF_SISTER));
        RECONCILED_GROUPS.put("children_count", List.of(
                RelationshipType.SON, RelationshipType.DAUGHTER, RelationshipType.ADOPTED_SON, RelationshipType.ADOPTED_DAUGHTER));
        RECONCILED_GROUPS.put("aunt_uncle_count", List.of(RelationshipType.AUNT, RelationshipType.UNCLE));
        RECONCILED_GROUPS.put("cousin_count", List.of(RelationshipType.COUSIN));
        RECONCILED_GROUPS.put("niece_nephew_count", List.of(RelationshipType.NIECE, RelationshipType.NEPHEW));
    }

    private static final Map<String, String> GROUP_LABELS = Map.of(
            "sibling_count", "Siblings",
            "children_count", "Children",
            "aunt_uncle_count", "Aunts/Uncles",
            "cousin_count", "Cousins",
            "niece_nephew_count", "Nieces/Nephews");

    private final QuestionnaireSessionRepository sessionRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final FamilyMemberRoleRepository familyMemberRoleRepository;
    private final QuestionResponseRepository questionResponseRepository;
    private final ParentChildRelationshipRepository parentChildRelationshipRepository;
    private final PartnerRelationshipRepository partnerRelationshipRepository;
    private final IndividualResolver individualResolver;
    private final JsonColumnMapper jsonColumnMapper;

    @Transactional
    public FamilyMemberResponse create(UUID sessionId, FamilyMemberCreateRequest request) {
        QuestionnaireSession session = findSessionOrThrow(sessionId);
        requireInProgress(session);
        Person proband = session.getPerson();

        UUID scopeId = request.scopeId() != null ? request.scopeId() : proband.getId();
        IndividualRef scope = individualResolver.resolve(sessionId, scopeId);

        if (SINGULAR_RELATIONSHIP_TYPES.contains(request.relationshipType()) && scopeHasRole(scope, request.relationshipType())) {
            throw new DuplicateResourceException(
                    "A family member with relationshipType " + request.relationshipType()
                            + " already exists for scope " + scopeId);
        }

        FamilyMember familyMember = new FamilyMember();
        familyMember.setProband(proband);
        familyMember.setMaternalPaternalSide(request.maternalPaternalSide());
        LocalDateTime now = LocalDateTime.now();
        familyMember.setCreatedAt(now);
        familyMember.setUpdatedAt(now);
        familyMember = familyMemberRepository.save(familyMember);

        FamilyMemberRole role = new FamilyMemberRole();
        role.setFamilyMember(familyMember);
        role.setScopePerson(scope.person());
        role.setScopeFamilyMember(scope.familyMember());
        role.setRelationshipType(request.relationshipType());
        role.setCreatedAt(now);
        familyMemberRoleRepository.save(role);

        autoCreateGraphEdges(proband, scope, familyMember, request.relationshipType(), now);

        return FamilyMemberResponse.from(familyMember, rolesOf(familyMember.getId()), jsonColumnMapper);
    }

    private boolean scopeHasRole(IndividualRef scope, RelationshipType type) {
        return scope.person() != null
                ? familyMemberRoleRepository.existsByScopePersonIdAndRelationshipType(scope.person().getId(), type)
                : familyMemberRoleRepository.existsByScopeFamilyMemberIdAndRelationshipType(scope.familyMember().getId(), type);
    }

    // Persists the graph edge(s) a role implies, so a client never has to make a separate
    // relationships/parent-child or relationships/partners call for the common case. See
    // QP-Family-Relationship-Persistence-Design.md §2.11-12.
    private void autoCreateGraphEdges(Person proband, IndividualRef scope, FamilyMember newMember,
                                       RelationshipType relationshipType, LocalDateTime now) {
        if (CHILD_OF_SCOPE_TYPES.contains(relationshipType)) {
            // newMember is scope's child - always a valid edge, scope can be proband or family member.
            saveParentChild(proband, scope, newMember, now);
            autoAttachSecondParent(proband, scope, newMember, now);
        } else if (PARENT_OF_SCOPE_TYPES.contains(relationshipType)) {
            // newMember is scope's parent. Only createable when scope is itself a family member -
            // when scope is the proband, ParentChildRelationship can never record it (the proband
            // can never be the child there), so it stays implicit, exactly as it always has been.
            if (scope.familyMember() != null) {
                saveParentChild(proband, new IndividualRef(null, newMember), scope.familyMember(), now);
            }
        } else if (relationshipType == RelationshipType.SPOUSE) {
            savePartner(proband, scope, newMember, now);
        }
        // Every other type (siblings, grandparents, aunts/uncles/cousins/nieces/nephews) needs an
        // explicit relationships/parent-child or relationships/partners call, unchanged from today.
    }

    private void saveParentChild(Person proband, IndividualRef parent, FamilyMember child, LocalDateTime now) {
        ParentChildRelationship relationship = new ParentChildRelationship();
        relationship.setProband(proband);
        relationship.setParentPerson(parent.person());
        relationship.setParentFamilyMember(parent.familyMember());
        relationship.setChildFamilyMember(child);
        relationship.setCreatedAt(now);
        parentChildRelationshipRepository.save(relationship);
    }

    private void savePartner(Person proband, IndividualRef personA, FamilyMember personBFamilyMember, LocalDateTime now) {
        PartnerRelationship relationship = new PartnerRelationship();
        relationship.setProband(proband);
        relationship.setPersonAPerson(personA.person());
        relationship.setPersonAFamilyMember(personA.familyMember());
        relationship.setPersonBFamilyMember(personBFamilyMember);
        relationship.setMatingLineType(MatingLineType.MATING_LINE);
        relationship.setReproductiveStatus(ReproductiveStatusType.UNKNOWN);
        relationship.setCreatedAt(now);
        partnerRelationshipRepository.save(relationship);
    }

    // When a child is added under a scope that has exactly one partner relationship on record with
    // reproductiveStatus = HAS_CHILDREN, that partner is auto-attached as the second parent - this
    // is what makes "add a sibling under mother's scope" produce a genuine full sibling without the
    // client re-stating father. Gated on HAS_CHILDREN specifically so a step-relationship isn't
    // wrongly guessed into biological co-parentage. Only evaluated at child-creation time in this
    // pass - re-evaluating retroactively when a PartnerRelationship is recorded afterward is
    // deferred (see QP-Family-Relationship-Persistence-Design.md §5).
    private void autoAttachSecondParent(Person proband, IndividualRef scope, FamilyMember child, LocalDateTime now) {
        UUID scopeId = scope.id();
        List<PartnerRelationship> scopePartnerships = partnerRelationshipRepository
                .findAllByProbandIdOrderByCreatedAtAsc(proband.getId()).stream()
                .filter(pr -> involves(pr, scopeId))
                .filter(pr -> pr.getReproductiveStatus() == ReproductiveStatusType.HAS_CHILDREN)
                .toList();
        if (scopePartnerships.size() != 1) {
            return;
        }
        PartnerRelationship partnership = scopePartnerships.get(0);
        IndividualRef otherParent = otherParticipant(partnership, scopeId);
        saveParentChild(proband, otherParent, child, now);
    }

    private boolean involves(PartnerRelationship relationship, UUID individualId) {
        UUID aId = relationship.getPersonAPerson() != null ? relationship.getPersonAPerson().getId() : relationship.getPersonAFamilyMember().getId();
        UUID bId = relationship.getPersonBPerson() != null ? relationship.getPersonBPerson().getId() : relationship.getPersonBFamilyMember().getId();
        return aId.equals(individualId) || bId.equals(individualId);
    }

    private IndividualRef otherParticipant(PartnerRelationship relationship, UUID notThisId) {
        UUID aId = relationship.getPersonAPerson() != null ? relationship.getPersonAPerson().getId() : relationship.getPersonAFamilyMember().getId();
        if (!aId.equals(notThisId)) {
            return new IndividualRef(relationship.getPersonAPerson(), relationship.getPersonAFamilyMember());
        }
        return new IndividualRef(relationship.getPersonBPerson(), relationship.getPersonBFamilyMember());
    }

    public List<FamilyMemberResponse> getAll(UUID sessionId) {
        Person proband = findSessionOrThrow(sessionId).getPerson();
        List<FamilyMember> familyMembers = familyMemberRepository.findAllByProbandIdOrderByCreatedAtAsc(proband.getId());
        List<UUID> ids = familyMembers.stream().map(FamilyMember::getId).toList();
        Map<UUID, List<FamilyMemberRoleView>> rolesByMember = new LinkedHashMap<>();
        if (!ids.isEmpty()) {
            for (FamilyMemberRole role : familyMemberRoleRepository.findAllByFamilyMemberIdIn(ids)) {
                rolesByMember.computeIfAbsent(role.getFamilyMember().getId(), k -> new ArrayList<>())
                        .add(FamilyMemberRoleView.from(role));
            }
        }
        return familyMembers.stream()
                .map(fm -> FamilyMemberResponse.from(fm, rolesByMember.getOrDefault(fm.getId(), List.of()), jsonColumnMapper))
                .toList();
    }

    public FamilyMemberResponse getById(UUID sessionId, UUID familyMemberId) {
        FamilyMember familyMember = findFamilyMemberOrThrow(sessionId, familyMemberId);
        return FamilyMemberResponse.from(familyMember, rolesOf(familyMemberId), jsonColumnMapper);
    }

    private List<FamilyMemberRoleView> rolesOf(UUID familyMemberId) {
        return familyMemberRoleRepository.findAllByFamilyMemberId(familyMemberId).stream()
                .map(FamilyMemberRoleView::from)
                .toList();
    }

    // FR-13.2/13.3: read-only comparison of actual FAMILY_MEMBER counts (by role relative to the
    // proband specifically) against whatever was declared in Person.additionalFacts - never
    // blocks adding more than declared, just reports it.
    public List<RelativeCountReconciliation> reconcileRelativeCounts(UUID sessionId) {
        Person proband = findSessionOrThrow(sessionId).getPerson();
        List<FamilyMemberRole> probandScopedRoles = familyMemberRoleRepository
                .findAllByFamilyMemberIdIn(familyMemberRepository.findAllByProbandIdOrderByCreatedAtAsc(proband.getId())
                        .stream().map(FamilyMember::getId).toList())
                .stream()
                .filter(role -> role.getScopePerson() != null && role.getScopePerson().getId().equals(proband.getId()))
                .toList();
        JsonNode additionalFacts = jsonColumnMapper.read(proband.getAdditionalFacts());

        List<RelativeCountReconciliation> report = new ArrayList<>();
        for (Map.Entry<String, List<RelationshipType>> group : RECONCILED_GROUPS.entrySet()) {
            String factKey = group.getKey();
            List<RelationshipType> types = group.getValue();

            long actualCount = probandScopedRoles.stream().filter(role -> types.contains(role.getRelationshipType())).count();
            Integer declaredCount = (additionalFacts != null && additionalFacts.has(factKey) && !additionalFacts.get(factKey).isNull())
                    ? additionalFacts.get(factKey).asInt() : null;
            boolean matches = declaredCount == null || declaredCount == actualCount;

            report.add(new RelativeCountReconciliation(GROUP_LABELS.get(factKey), factKey, declaredCount, actualCount, matches));
        }
        return report;
    }

    @Transactional
    public void delete(UUID sessionId, UUID familyMemberId) {
        FamilyMember familyMember = findFamilyMemberOrThrow(sessionId, familyMemberId);
        QuestionnaireSession session = findSessionOrThrow(sessionId);
        requireInProgress(session);
        if (questionResponseRepository.existsByRelationshipInstanceId(familyMemberId)) {
            throw new ResourceInUseException(
                    "Family member " + familyMemberId + " already has recorded answers and cannot be deleted");
        }
        if (parentChildRelationshipRepository.existsByParentFamilyMemberIdOrChildFamilyMemberId(familyMemberId, familyMemberId)) {
            throw new ResourceInUseException(
                    "Family member " + familyMemberId + " is referenced by a parent-child relationship and cannot be deleted");
        }
        if (partnerRelationshipRepository.existsByPersonAFamilyMemberIdOrPersonBFamilyMemberId(familyMemberId, familyMemberId)) {
            throw new ResourceInUseException(
                    "Family member " + familyMemberId + " is referenced by a partner relationship and cannot be deleted");
        }
        if (familyMemberRoleRepository.existsByScopeFamilyMemberId(familyMemberId)) {
            // This person is the scope (anchor) for another family member's role - deleting them
            // would orphan that role, same reasoning as the relationship-reference checks above.
            throw new ResourceInUseException(
                    "Family member " + familyMemberId + " is the scope for another family member's role and cannot be deleted");
        }
        familyMemberRoleRepository.deleteAll(familyMemberRoleRepository.findAllByFamilyMemberId(familyMemberId));
        familyMemberRepository.delete(familyMember);
    }

    // Package-private so AssessmentService can reuse the same "does this family member belong
    // to this session's proband" scoping check when starting/applying a pass for one.
    FamilyMember findFamilyMemberOrThrow(UUID sessionId, UUID familyMemberId) {
        Person proband = findSessionOrThrow(sessionId).getPerson();
        return familyMemberRepository.findByIdAndProbandId(familyMemberId, proband.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Family member " + familyMemberId + " not found for session " + sessionId));
    }

    private QuestionnaireSession findSessionOrThrow(UUID sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session " + sessionId + " not found"));
    }

    // Session/request integrity: same rule as AssessmentService's own requireInProgress - a
    // completed session is read-only everywhere except resume-at-template.
    private void requireInProgress(QuestionnaireSession session) {
        if (session.getStatus() == SessionStatus.COMPLETED) {
            throw new ResourceInUseException(
                    "Session " + session.getId() + " is already completed and cannot be modified");
        }
    }
}
