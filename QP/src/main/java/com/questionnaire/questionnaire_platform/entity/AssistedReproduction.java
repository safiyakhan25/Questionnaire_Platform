package com.questionnaire.questionnaire_platform.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

// One row per individual with an assisted-reproduction record. Maps to
// individual.assistedReproduction (pedigree_schema.json).
@Entity
@Table(name = "assisted_reproduction")
@Getter
@Setter
@NoArgsConstructor
public class AssistedReproduction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proband_id", nullable = false)
    private Person proband;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "individual_person_id")
    private Person individualPerson;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "individual_family_member_id")
    private FamilyMember individualFamilyMember;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "reproduction_type", nullable = false)
    private AssistedReproductionType reproductionType;

    @Column(name = "anonymous")
    private Boolean anonymous;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
