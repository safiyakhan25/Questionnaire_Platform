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
@Table(name = "family_member")
@Getter
@Setter
@NoArgsConstructor
public class FamilyMember {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proband_id", nullable = false)
    private Person proband;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "relationship_type", nullable = false)
    private RelationshipType relationshipType;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "maternal_paternal_side", nullable = false)
    private MaternalPaternalSideType maternalPaternalSide;

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
    @Column(name = "gender_at_birth")
    private GenderAtBirthType genderAtBirth;

    @Column(name = "gender_identity")
    private String genderIdentity;

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

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
