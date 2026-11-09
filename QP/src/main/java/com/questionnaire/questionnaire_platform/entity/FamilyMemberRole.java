package com.questionnaire.questionnaire_platform.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

// One relational label a FAMILY_MEMBER holds, relative to one scope (the proband or another
// family member) - a person can hold more than one once recursive adding is allowed (e.g. SPOUSE
// relative to the father's scope, MOTHER relative to the proband's scope, both true at once).
// scopePerson/scopeFamilyMember is XOR, same idiom PARENT_CHILD_RELATIONSHIP/PARTNER_RELATIONSHIP
// already use. Append-only - a correction is an explicit delete, never an overwrite of this row.
// See QP-Family-Relationship-Persistence-Design.md.
@Entity
@Table(name = "family_member_role")
@Getter
@Setter
@NoArgsConstructor
public class FamilyMemberRole {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "family_member_id", nullable = false)
    private FamilyMember familyMember;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scope_person_id")
    private Person scopePerson;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scope_family_member_id")
    private FamilyMember scopeFamilyMember;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "relationship_type", nullable = false)
    private RelationshipType relationshipType;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
