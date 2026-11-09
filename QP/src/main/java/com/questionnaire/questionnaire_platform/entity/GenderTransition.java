package com.questionnaire.questionnaire_platform.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

// At most one row per individual (person_id/family_member_id XOR, each uniquely indexed). Maps to
// individual.genderTransition (pedigree_schema.json).
@Entity
@Table(name = "gender_transition")
@Getter
@Setter
@NoArgsConstructor
public class GenderTransition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id")
    private Person person;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "family_member_id")
    private FamilyMember familyMember;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "from_gender", nullable = false)
    private GenderIdentityType fromGender;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "to_gender", nullable = false)
    private GenderIdentityType toGender;

    @Column(name = "transition_year")
    private Integer transitionYear;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
