package com.questionnaire.questionnaire_platform.repository;

import com.questionnaire.questionnaire_platform.entity.Adoption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AdoptionRepository extends JpaRepository<Adoption, UUID> {

    Optional<Adoption> findByIndividualPersonId(UUID personId);

    Optional<Adoption> findByIndividualFamilyMemberId(UUID familyMemberId);

    void deleteByIndividualPersonId(UUID personId);

    void deleteByIndividualFamilyMemberId(UUID familyMemberId);
}
