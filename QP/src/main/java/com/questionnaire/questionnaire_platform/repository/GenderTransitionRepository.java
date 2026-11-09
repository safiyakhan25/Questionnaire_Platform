package com.questionnaire.questionnaire_platform.repository;

import com.questionnaire.questionnaire_platform.entity.GenderTransition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface GenderTransitionRepository extends JpaRepository<GenderTransition, UUID> {

    Optional<GenderTransition> findByPersonId(UUID personId);

    Optional<GenderTransition> findByFamilyMemberId(UUID familyMemberId);

    void deleteByPersonId(UUID personId);

    void deleteByFamilyMemberId(UUID familyMemberId);
}
