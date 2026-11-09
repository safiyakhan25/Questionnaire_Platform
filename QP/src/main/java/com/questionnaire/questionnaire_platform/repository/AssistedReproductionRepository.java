package com.questionnaire.questionnaire_platform.repository;

import com.questionnaire.questionnaire_platform.entity.AssistedReproduction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AssistedReproductionRepository extends JpaRepository<AssistedReproduction, UUID> {

    Optional<AssistedReproduction> findByIndividualPersonId(UUID personId);

    Optional<AssistedReproduction> findByIndividualFamilyMemberId(UUID familyMemberId);

    void deleteByIndividualPersonId(UUID personId);

    void deleteByIndividualFamilyMemberId(UUID familyMemberId);
}
