package com.questionnaire.questionnaire_platform.repository;

import com.questionnaire.questionnaire_platform.entity.AdoptionParent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AdoptionParentRepository extends JpaRepository<AdoptionParent, UUID> {

    List<AdoptionParent> findAllByAdoptionId(UUID adoptionId);

    void deleteAllByAdoptionId(UUID adoptionId);
}
