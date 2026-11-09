package com.questionnaire.questionnaire_platform.repository;

import com.questionnaire.questionnaire_platform.entity.FamilyMemberRole;
import com.questionnaire.questionnaire_platform.entity.RelationshipType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FamilyMemberRoleRepository extends JpaRepository<FamilyMemberRole, UUID> {

    List<FamilyMemberRole> findAllByFamilyMemberId(UUID familyMemberId);

    List<FamilyMemberRole> findAllByFamilyMemberIdIn(List<UUID> familyMemberIds);

    // FR-13.1 singular-type check, now scoped per anchor instead of globally per proband.
    boolean existsByScopePersonIdAndRelationshipType(UUID scopePersonId, RelationshipType relationshipType);

    boolean existsByScopeFamilyMemberIdAndRelationshipType(UUID scopeFamilyMemberId, RelationshipType relationshipType);

    // Deletion-safety: a family member who is themselves the scope for another role can't be
    // removed without orphaning that role.
    boolean existsByScopeFamilyMemberId(UUID scopeFamilyMemberId);
}
