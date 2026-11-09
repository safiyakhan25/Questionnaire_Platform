package com.questionnaire.questionnaire_platform.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

// One row per twin GROUP (2 for twins, 3+ for higher-order multiples) - the zygosity/type is a
// fact about the twinning event, shared by every co-twin (see TwinshipMember), not duplicated on
// each member's own row. Maps to individual.twinship (pedigree_schema.json).
@Entity
@Table(name = "twinship")
@Getter
@Setter
@NoArgsConstructor
public class Twinship {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proband_id", nullable = false)
    private Person proband;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "twin_type", nullable = false)
    private TwinType twinType;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
