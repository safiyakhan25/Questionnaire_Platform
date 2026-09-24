package com.questionnaire.questionnaire_platform.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

// Belongs to exactly one of person or family_member, never both, never neither.
// Enforced at the DB level with an XOR check constraint (see the migration) since it's a data-integrity invariant, not a UX concern.
@Entity
@Table(name = "disease")
@Getter
@Setter
@NoArgsConstructor
public class Disease {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "disease_id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id")
    private Person person;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "family_member_id")
    private FamilyMember familyMember;

    @Column(name = "disease_type")
    private String diseaseType;

    @Column(name = "age_at_diagnosis")
    private Integer ageAtDiagnosis;
}
