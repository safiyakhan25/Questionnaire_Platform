package com.questionnaire.questionnaire_platform.repository;

import com.questionnaire.questionnaire_platform.entity.Mutation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MutationRepository extends JpaRepository<Mutation, UUID> {

    List<Mutation> findAllByPersonId(UUID personId);

    List<Mutation> findAllByFamilyMemberId(UUID familyMemberId);

    void deleteAllByPersonId(UUID personId);

    void deleteAllByFamilyMemberId(UUID familyMemberId);
}
