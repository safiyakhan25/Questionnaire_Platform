package com.questionnaire.questionnaire_platform.repository;

import com.questionnaire.questionnaire_platform.entity.PartnerRelationship;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PartnerRelationshipRepository extends JpaRepository<PartnerRelationship, UUID> {

    List<PartnerRelationship> findAllByProbandIdOrderByCreatedAtAsc(UUID probandId);

    Optional<PartnerRelationship> findByIdAndProbandId(UUID id, UUID probandId);

    // Deletion-safety: same reasoning as ParentChildRelationshipRepository's equivalent check.
    boolean existsByPersonAFamilyMemberIdOrPersonBFamilyMemberId(UUID personAFamilyMemberId, UUID personBFamilyMemberId);
}
