package com.questionnaire.questionnaire_platform.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

// A pregnancy loss or current pregnancy gets its own FAMILY_MEMBER row (never the proband), with
// this table attached - at most one outcome record per family member. Maps to
// individual.pregnancyOutcome (pedigree_schema.json).
@Entity
@Table(name = "pregnancy_outcome")
@Getter
@Setter
@NoArgsConstructor
public class PregnancyOutcome {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "family_member_id", nullable = false)
    private FamilyMember familyMember;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "outcome_type", nullable = false)
    private PregnancyOutcomeType outcomeType;

    @Column(name = "gestational_weeks")
    private Integer gestationalWeeks;

    @Column(name = "lmp_date")
    private LocalDate lmpDate;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
