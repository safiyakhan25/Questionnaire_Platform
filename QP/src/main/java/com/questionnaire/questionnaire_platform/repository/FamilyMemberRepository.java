package com.questionnaire.questionnaire_platform.repository;

import com.questionnaire.questionnaire_platform.entity.FamilyMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FamilyMemberRepository extends JpaRepository<FamilyMember, UUID> {

    List<FamilyMember> findAllByProbandIdOrderByCreatedAtAsc(UUID probandId);

    Optional<FamilyMember> findByIdAndProbandId(UUID id, UUID probandId);

    // FR-13.1's singular-type check moved to FamilyMemberRoleRepository, scoped per anchor now
    // instead of globally per proband (see QP-Family-Relationship-Persistence-Design.md).
}
