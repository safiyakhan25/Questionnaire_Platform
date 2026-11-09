package com.questionnaire.questionnaire_platform.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "person")
@Getter
@Setter
@NoArgsConstructor
public class Person {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "middle_name")
    private String middleName;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "preferred_name")
    private String preferredName;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "sex_assigned_at_birth")
    private SexAssignedAtBirthType sexAssignedAtBirth;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "gender_identity")
    private GenderIdentityType genderIdentity;

    // Independent of sexAssignedAtBirth/genderIdentity - intersex is annotation-only, never a value
    // of either enum. QP-Pedigree-Schema-Design.md §3.2.
    @Column(name = "is_intersex")
    private Boolean isIntersex;

    // ISO 3166-1 alpha-2 codes, one per country of family origin (ancestry can span more than one) -
    // array, matching race/ethnicity's own TEXT[] convention on this table. Per-code format enforced
    // at the question-authoring layer (validation.pattern), not here.
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "country_of_origin", columnDefinition = "text[]")
    private String[] countryOfOrigin;

    @Column(name = "birth_date")
    private LocalDateTime birthDate;

    @Column(name = "marital_status")
    private String maritalStatus;

    @Column(name = "communication_language")
    private String communicationLanguage;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private String[] race;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private String[] ethnicity;

    // Deliberately VARCHAR, not a lookup-table FK: the answer is captured verbatim from the questionnaire response, no separate ID needed. Confirmed with mentor 2026-08-13.
    @Column(name = "blood_type")
    private String bloodType;

    @Column(name = "deceased")
    private Boolean deceased;

    @Column(name = "deceased_date_time")
    private LocalDateTime deceasedDateTime;

    @Column(name = "age")
    private Integer age;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String contact;

    @Column(name = "primary_contact")
    private String primaryContact;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String address;

    // Flexible bucket for collected facts that don't map onto a fixed column.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "additional_facts", columnDefinition = "jsonb")
    private String additionalFacts;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
