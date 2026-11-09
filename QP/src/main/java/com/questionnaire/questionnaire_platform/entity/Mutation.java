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
// Maps to individual.geneticTests[] (pedigree_schema.json) - see QP-Pedigree-Schema-Design.md §2.5.
// Table name is unchanged from its original free-text shape, but its semantics going forward are
// "genetic test result", not just "a mutation".
@Entity
@Table(name = "mutation")
@Getter
@Setter
@NoArgsConstructor
public class Mutation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "mutation_id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id")
    private Person person;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "family_member_id")
    private FamilyMember familyMember;

    @Column(nullable = false)
    private String gene;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private MutationResultType result;

    private String variant;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "test_type")
    private GeneticTestType testType;
}
