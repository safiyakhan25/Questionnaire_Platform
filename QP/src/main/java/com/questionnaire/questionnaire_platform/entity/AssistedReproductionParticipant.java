package com.questionnaire.questionnaire_platform.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

// Flattens assistedReproduction's six single/array participant fields (eggDonorId, spermDonorId,
// gestationalCarrierId, intendedParents[], donatedTo[], carriedFor[]) into one role-discriminated
// child table, rather than six mostly-null columns on the parent row.
@Entity
@Table(name = "assisted_reproduction_participant")
@Getter
@Setter
@NoArgsConstructor
public class AssistedReproductionParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assisted_reproduction_id", nullable = false)
    private AssistedReproduction assistedReproduction;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "participant_role", nullable = false)
    private AssistedReproductionParticipantRole participantRole;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participant_person_id")
    private Person participantPerson;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participant_family_member_id")
    private FamilyMember participantFamilyMember;
}
