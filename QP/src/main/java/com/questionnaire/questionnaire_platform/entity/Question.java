package com.questionnaire.questionnaire_platform.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(name = "question")
@Getter
@Setter
@NoArgsConstructor
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "answer_key", nullable = false, unique = true)
    private String answerKey;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String text;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private QuestionType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private Template template;

    @Column(name = "display_order")
    private Integer displayOrder;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String validation;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String options;

    // Determines whether this question exists in the rendered form at all. The only rule
    // type a question carries - disable_rule was removed once FR-11 (a rule may only
    // reference an earlier template, never its own) made in-template re-evaluation
    // impossible: nothing in the currently-rendered template can ever change as a result
    // of answering another question in that same template.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "visibility_rule", columnDefinition = "jsonb")
    private String visibilityRule;

    // Only set for STRUCTURED questions: {shape: "object"|"list", fields: [...]} - see
    // docs/superpowers/specs/2026-11-01-structured-pedigree-questions-design.md §4.2.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "field_config", columnDefinition = "jsonb")
    private String fieldConfig;
}
