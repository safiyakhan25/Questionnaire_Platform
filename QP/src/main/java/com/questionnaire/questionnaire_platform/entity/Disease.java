package com.questionnaire.questionnaire_platform.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

// Belongs to exactly one of person or family_member, never both, never neither.
// Enforced at the DB level with an XOR check constraint (see the migration) since it's a data-integrity invariant, not a UX concern.
// Maps to individual.conditions[] (pedigree_schema.json) - see QP-Pedigree-Schema-Design.md §2.4.
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "disease_catalog_id", nullable = false)
    private DiseaseCatalog diseaseCatalog;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "code_system", nullable = false)
    private DiseaseCodeSystemType codeSystem;

    @Column(name = "code_id", nullable = false)
    private String codeId;

    @Column(name = "code_label")
    private String codeLabel;

    @Column(name = "age_at_diagnosis")
    private Integer ageAtDiagnosis;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private DiseaseLateralityType laterality;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private DiseaseStatusType status;
}
