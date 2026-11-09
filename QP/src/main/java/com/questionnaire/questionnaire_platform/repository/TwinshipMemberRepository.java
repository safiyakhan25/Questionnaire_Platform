package com.questionnaire.questionnaire_platform.repository;

import com.questionnaire.questionnaire_platform.entity.TwinshipMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TwinshipMemberRepository extends JpaRepository<TwinshipMember, UUID> {

    Optional<TwinshipMember> findByPersonId(UUID personId);

    Optional<TwinshipMember> findByFamilyMemberId(UUID familyMemberId);

    List<TwinshipMember> findAllByTwinshipId(UUID twinshipId);

    void deleteByPersonId(UUID personId);

    void deleteByFamilyMemberId(UUID familyMemberId);
}
