package com.questionnaire.questionnaire_platform.service;

import com.questionnaire.questionnaire_platform.dto.PartnerRelationshipCreateRequest;
import com.questionnaire.questionnaire_platform.dto.PartnerRelationshipResponse;
import com.questionnaire.questionnaire_platform.entity.MatingLineType;
import com.questionnaire.questionnaire_platform.entity.PartnerRelationship;
import com.questionnaire.questionnaire_platform.entity.Person;
import com.questionnaire.questionnaire_platform.entity.QuestionnaireSession;
import com.questionnaire.questionnaire_platform.entity.RelationshipStatusType;
import com.questionnaire.questionnaire_platform.entity.ReproductiveStatusType;
import com.questionnaire.questionnaire_platform.entity.SessionStatus;
import com.questionnaire.questionnaire_platform.exception.InvalidRelationshipException;
import com.questionnaire.questionnaire_platform.exception.ResourceInUseException;
import com.questionnaire.questionnaire_platform.exception.ResourceNotFoundException;
import com.questionnaire.questionnaire_platform.repository.PartnerRelationshipRepository;
import com.questionnaire.questionnaire_platform.repository.QuestionnaireSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

// A partner/mating-line edge is always recorded in the context of one session's pedigree, scoped
// to that session's own proband - same pattern FamilyMemberService/ParentChildRelationshipService
// already use. The three conditional field rules (QP-Pedigree-Schema-Design.md §2.3) are enforced
// here, not left to export time - they're data-collection rules.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PartnerRelationshipService {

    private static final Set<MatingLineType> ENDED_YEAR_ALLOWED_MATING_LINE = EnumSet.of(
            MatingLineType.SEPARATED, MatingLineType.DIVORCED, MatingLineType.NO_LONGER_TOGETHER);

    // relationshipStatus is the field the questionnaire actually asks (matingLineType is no longer
    // directly asked - see entity comment); endedYear must gate on whichever of the two a caller
    // actually set, not only the pedigree-rendering field most callers won't touch.
    private static final Set<RelationshipStatusType> ENDED_YEAR_ALLOWED_RELATIONSHIP_STATUS = EnumSet.of(
            RelationshipStatusType.SEPARATED, RelationshipStatusType.DIVORCED);

    private final QuestionnaireSessionRepository sessionRepository;
    private final PartnerRelationshipRepository partnerRelationshipRepository;
    private final IndividualResolver individualResolver;

    @Transactional
    public PartnerRelationshipResponse create(UUID sessionId, PartnerRelationshipCreateRequest request) {
        QuestionnaireSession session = findSessionOrThrow(sessionId);
        requireInProgress(session);
        if (request.personAId().equals(request.personBId())) {
            throw new InvalidRelationshipException("The two partners cannot be the same individual");
        }

        MatingLineType matingLineType = request.matingLineType() != null ? request.matingLineType() : MatingLineType.MATING_LINE;
        ReproductiveStatusType reproductiveStatus = request.reproductiveStatus() != null
                ? request.reproductiveStatus() : ReproductiveStatusType.UNKNOWN;
        validateConditionalFields(matingLineType, request.relationshipStatus(), reproductiveStatus,
                request.unshownChildrenCount(), request.consanguineous(), request.consanguinityDegree(), request.endedYear());

        IndividualRef personA = individualResolver.resolve(sessionId, request.personAId());
        IndividualRef personB = individualResolver.resolve(sessionId, request.personBId());

        PartnerRelationship relationship = new PartnerRelationship();
        relationship.setProband(session.getPerson());
        relationship.setPersonAPerson(personA.person());
        relationship.setPersonAFamilyMember(personA.familyMember());
        relationship.setPersonBPerson(personB.person());
        relationship.setPersonBFamilyMember(personB.familyMember());
        relationship.setMatingLineType(matingLineType);
        relationship.setReproductiveStatus(reproductiveStatus);
        relationship.setRelationshipStatus(request.relationshipStatus());
        relationship.setConsanguineous(request.consanguineous());
        relationship.setUnshownChildrenCount(request.unshownChildrenCount());
        relationship.setConsanguinityDegree(request.consanguinityDegree());
        relationship.setEndedYear(request.endedYear());
        relationship.setCreatedAt(LocalDateTime.now());

        return PartnerRelationshipResponse.from(partnerRelationshipRepository.save(relationship));
    }

    public List<PartnerRelationshipResponse> getAll(UUID sessionId) {
        Person proband = findSessionOrThrow(sessionId).getPerson();
        return partnerRelationshipRepository.findAllByProbandIdOrderByCreatedAtAsc(proband.getId()).stream()
                .map(PartnerRelationshipResponse::from)
                .toList();
    }

    public PartnerRelationshipResponse getById(UUID sessionId, UUID relationshipId) {
        return PartnerRelationshipResponse.from(findRelationshipOrThrow(sessionId, relationshipId));
    }

    @Transactional
    public void delete(UUID sessionId, UUID relationshipId) {
        requireInProgress(findSessionOrThrow(sessionId));
        partnerRelationshipRepository.delete(findRelationshipOrThrow(sessionId, relationshipId));
    }

    private void validateConditionalFields(MatingLineType matingLineType, RelationshipStatusType relationshipStatus,
                                            ReproductiveStatusType reproductiveStatus, Integer unshownChildrenCount,
                                            Boolean consanguineous, String consanguinityDegree, Integer endedYear) {
        if (reproductiveStatus == ReproductiveStatusType.HAS_CHILDREN_NOT_SHOWN && unshownChildrenCount == null) {
            throw new InvalidRelationshipException(
                    "unshownChildrenCount is required when reproductiveStatus is HAS_CHILDREN_NOT_SHOWN");
        }
        if (Boolean.TRUE.equals(consanguineous) && (consanguinityDegree == null || consanguinityDegree.isBlank())) {
            throw new InvalidRelationshipException(
                    "consanguinityDegree is required when consanguineous is true");
        }
        if (endedYear != null && !ENDED_YEAR_ALLOWED_MATING_LINE.contains(matingLineType)
                && !ENDED_YEAR_ALLOWED_RELATIONSHIP_STATUS.contains(relationshipStatus)) {
            throw new InvalidRelationshipException(
                    "endedYear is only valid when matingLineType is SEPARATED, DIVORCED, or NO_LONGER_TOGETHER, "
                            + "or relationshipStatus is SEPARATED or DIVORCED");
        }
    }

    private PartnerRelationship findRelationshipOrThrow(UUID sessionId, UUID relationshipId) {
        Person proband = findSessionOrThrow(sessionId).getPerson();
        return partnerRelationshipRepository.findByIdAndProbandId(relationshipId, proband.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Partner relationship " + relationshipId + " not found for session " + sessionId));
    }

    private QuestionnaireSession findSessionOrThrow(UUID sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session " + sessionId + " not found"));
    }

    private void requireInProgress(QuestionnaireSession session) {
        if (session.getStatus() == SessionStatus.COMPLETED) {
            throw new ResourceInUseException(
                    "Session " + session.getId() + " is already completed and cannot be modified");
        }
    }
}
