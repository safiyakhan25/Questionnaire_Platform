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
@Table(name = "template")
@Getter
@Setter
@NoArgsConstructor
public class Template {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_id", nullable = false)
    private Workflow workflow;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    // Within a workflow, the template with the lowest displayOrder is the default/entry template and is always loaded first (its templateRule, if any, is not evaluated). Later templates load in displayOrder sequence, each gated by templateRule.
    @Column(name = "display_order")
    private Integer displayOrder;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "template_rule", columnDefinition = "jsonb")
    private String templateRule;

    // Who this template is collected for: PROBAND_ONLY (default, unchanged behavior), FAMILY_MEMBER_ONLY
    // (e.g. Pregnancy Outcome - never applies to the proband), or BOTH (e.g. Basic Details, reused as-is
    // for every family member's pass instead of being duplicated into a second workflow/template).
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "subject_scope", nullable = false)
    private SubjectScope subjectScope;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
