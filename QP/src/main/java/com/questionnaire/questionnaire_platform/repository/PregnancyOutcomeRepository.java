package com.questionnaire.questionnaire_platform.repository;

import com.questionnaire.questionnaire_platform.entity.PregnancyOutcome;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PregnancyOutcomeRepository extends JpaRepository<PregnancyOutcome, UUID> {

    Optional<PregnancyOutcome> findByFamilyMemberId(UUID familyMemberId);

    void deleteByFamilyMemberId(UUID familyMemberId);
}
