package com.questionnaire.questionnaire_platform.service;

import com.questionnaire.questionnaire_platform.dto.ParentChildRelationshipCreateRequest;
import com.questionnaire.questionnaire_platform.dto.ParentChildRelationshipResponse;
import com.questionnaire.questionnaire_platform.entity.ParentChildRelationship;
import com.questionnaire.questionnaire_platform.entity.Person;
import com.questionnaire.questionnaire_platform.entity.QuestionnaireSession;
import com.questionnaire.questionnaire_platform.entity.SessionStatus;
import com.questionnaire.questionnaire_platform.exception.InvalidRelationshipException;
import com.questionnaire.questionnaire_platform.exception.ResourceInUseException;
import com.questionnaire.questionnaire_platform.exception.ResourceNotFoundException;
import com.questionnaire.questionnaire_platform.repository.ParentChildRelationshipRepository;
import com.questionnaire.questionnaire_platform.repository.QuestionnaireSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

// A parent-child edge is always recorded in the context of one session's pedigree, scoped to that
// session's own proband - same pattern FamilyMemberService already uses.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ParentChildRelationshipService {

    private final QuestionnaireSessionRepository sessionRepository;
    private final ParentChildRelationshipRepository parentChildRelationshipRepository;
    private final IndividualResolver individualResolver;

    @Transactional
    public ParentChildRelationshipResponse create(UUID sessionId, ParentChildRelationshipCreateRequest request) {
        QuestionnaireSession session = findSessionOrThrow(sessionId);
        requireInProgress(session);
        if (request.parentId().equals(request.childFamilyMemberId())) {
            throw new InvalidRelationshipException("A parent and child cannot be the same individual");
        }

        IndividualRef parent = individualResolver.resolve(sessionId, request.parentId());
        IndividualRef child = individualResolver.resolve(sessionId, request.childFamilyMemberId());
        if (child.familyMember() == null) {
            throw new InvalidRelationshipException(
                    "The child must be an existing family member, not the proband - the proband's own "
                            + "parent link is recorded via FAMILY_MEMBER.relationship_type instead");
        }

        ParentChildRelationship relationship = new ParentChildRelationship();
        relationship.setProband(session.getPerson());
        relationship.setParentPerson(parent.person());
        relationship.setParentFamilyMember(parent.familyMember());
        relationship.setChildFamilyMember(child.familyMember());
        relationship.setCreatedAt(LocalDateTime.now());

        return ParentChildRelationshipResponse.from(parentChildRelationshipRepository.save(relationship));
    }

    public List<ParentChildRelationshipResponse> getAll(UUID sessionId) {
        Person proband = findSessionOrThrow(sessionId).getPerson();
        return parentChildRelationshipRepository.findAllByProbandIdOrderByCreatedAtAsc(proband.getId()).stream()
                .map(ParentChildRelationshipResponse::from)
                .toList();
    }

    public ParentChildRelationshipResponse getById(UUID sessionId, UUID relationshipId) {
        return ParentChildRelationshipResponse.from(findRelationshipOrThrow(sessionId, relationshipId));
    }

    @Transactional
    public void delete(UUID sessionId, UUID relationshipId) {
        requireInProgress(findSessionOrThrow(sessionId));
        parentChildRelationshipRepository.delete(findRelationshipOrThrow(sessionId, relationshipId));
    }

    private ParentChildRelationship findRelationshipOrThrow(UUID sessionId, UUID relationshipId) {
        Person proband = findSessionOrThrow(sessionId).getPerson();
        return parentChildRelationshipRepository.findByIdAndProbandId(relationshipId, proband.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Parent-child relationship " + relationshipId + " not found for session " + sessionId));
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
