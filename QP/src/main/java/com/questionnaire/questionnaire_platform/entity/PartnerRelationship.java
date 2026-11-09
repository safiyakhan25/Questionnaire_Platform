package com.questionnaire.questionnaire_platform.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

// One partner/mating-line edge, scoped to one proband's pedigree. personA/personB are each XOR
// (proband or family member), same idiom as DISEASE/MUTATION ownership. Deliberately redundant
// with FAMILY_MEMBER.relationship_type = SPOUSE - this table holds detail that field never could
// (mating line nuance, reproductive status, consanguinity). unshownChildrenCount/consanguinityDegree/
// endedYear are each conditionally required/valid depending on matingLineType/reproductiveStatus -
// enforced in PartnerRelationshipService, not just left to the exporter (QP-Pedigree-Schema-Design.md §2.3).
@Entity
@Table(name = "partner_relationship")
@Getter
@Setter
@NoArgsConstructor
public class PartnerRelationship {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proband_id", nullable = false)
    private Person proband;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_a_person_id")
    private Person personAPerson;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_a_family_member_id")
    private FamilyMember personAFamilyMember;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_b_person_id")
    private Person personBPerson;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_b_family_member_id")
    private FamilyMember personBFamilyMember;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "mating_line_type", nullable = false)
    private MatingLineType matingLineType;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "reproductive_status", nullable = false)
    private ReproductiveStatusType reproductiveStatus;

    // Independent of matingLineType - marital/separation history, decoupled per mentor review
    // 2026-10-14 ("a couple can be married and biologically related at the same time").
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "relationship_status")
    private RelationshipStatusType relationshipStatus;

    // Independent of matingLineType too - consanguinityDegree's required-when condition is now
    // keyed off this field, not matingLineType=CONSANGUINEOUS (PartnerRelationshipService).
    @Column(name = "consanguineous")
    private Boolean consanguineous;

    @Column(name = "unshown_children_count")
    private Integer unshownChildrenCount;

    @Column(name = "consanguinity_degree")
    private String consanguinityDegree;

    @Column(name = "ended_year")
    private Integer endedYear;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
