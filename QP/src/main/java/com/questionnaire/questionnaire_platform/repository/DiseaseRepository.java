package com.questionnaire.questionnaire_platform.repository;

import com.questionnaire.questionnaire_platform.entity.Disease;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DiseaseRepository extends JpaRepository<Disease, UUID> {

    List<Disease> findAllByPersonId(UUID personId);

    List<Disease> findAllByFamilyMemberId(UUID familyMemberId);

    void deleteAllByPersonId(UUID personId);

    void deleteAllByFamilyMemberId(UUID familyMemberId);
}
