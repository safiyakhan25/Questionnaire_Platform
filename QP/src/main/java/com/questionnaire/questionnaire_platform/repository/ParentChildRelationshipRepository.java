package com.questionnaire.questionnaire_platform.repository;

import com.questionnaire.questionnaire_platform.entity.ParentChildRelationship;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ParentChildRelationshipRepository extends JpaRepository<ParentChildRelationship, UUID> {

    List<ParentChildRelationship> findAllByProbandIdOrderByCreatedAtAsc(UUID probandId);

    Optional<ParentChildRelationship> findByIdAndProbandId(UUID id, UUID probandId);

    // Deletion-safety: a family member referenced as either side of an edge can't be removed
    // without orphaning that edge - checked explicitly in FamilyMemberService.delete() rather
    // than relying on the raw DB FK violation (no ON DELETE CASCADE on either column).
    boolean existsByParentFamilyMemberIdOrChildFamilyMemberId(UUID parentFamilyMemberId, UUID childFamilyMemberId);
}
