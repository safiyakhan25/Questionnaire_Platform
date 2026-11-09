package com.questionnaire.questionnaire_platform.repository;

import com.questionnaire.questionnaire_platform.entity.AssistedReproductionParticipant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AssistedReproductionParticipantRepository extends JpaRepository<AssistedReproductionParticipant, UUID> {

    List<AssistedReproductionParticipant> findAllByAssistedReproductionId(UUID assistedReproductionId);

    void deleteAllByAssistedReproductionId(UUID assistedReproductionId);
}
