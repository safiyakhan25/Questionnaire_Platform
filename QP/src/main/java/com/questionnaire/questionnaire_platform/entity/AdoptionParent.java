package com.questionnaire.questionnaire_platform.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

// Adoptive parents only (up to 2, per pedigree_schema.json's adoptiveParents maxItems). Biological
// parents are NOT stored here - they are derived at export time from the individual's existing
// PARENT_CHILD_RELATIONSHIP/FAMILY_MEMBER_ROLE data (the same source used for everyone's parents[]).
@Entity
@Table(name = "adoption_parent")
@Getter
@Setter
@NoArgsConstructor
public class AdoptionParent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "adoption_id", nullable = false)
    private Adoption adoption;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_person_id")
    private Person parentPerson;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_family_member_id")
    private FamilyMember parentFamilyMember;
}
