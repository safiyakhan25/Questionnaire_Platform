package com.questionnaire.questionnaire_platform.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

// One edge in the family relationship graph: parent -> child, scoped to one proband's pedigree.
// parentPerson/parentFamilyMember is XOR (the parent is either the proband or a family member) -
// enforced at the DB level, same idiom already used for DISEASE/MUTATION ownership. The proband is
// never the child here: their own parent link already exists via FAMILY_MEMBER.relationship_type.
// Siblings/half-siblings are deliberately NOT stored - derived at export time from shared parent
// edges (QP-Pedigree-Schema-Design.md §5), to avoid two rows disagreeing about the same fact.
@Entity
@Table(name = "parent_child_relationship")
@Getter
@Setter
@NoArgsConstructor
public class ParentChildRelationship {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proband_id", nullable = false)
    private Person proband;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_person_id")
    private Person parentPerson;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_family_member_id")
    private FamilyMember parentFamilyMember;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "child_family_member_id", nullable = false)
    private FamilyMember childFamilyMember;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
