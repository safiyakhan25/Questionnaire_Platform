package com.questionnaire.questionnaire_platform.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

// Append-only audit log of every answer ever submitted. Never updated or deleted, only inserted.
@Entity
@Table(name = "question_response")
@Getter
@Setter
@NoArgsConstructor
public class QuestionResponse {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "response_id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private QuestionnaireSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    // Denormalized from question.template_id for query efficiency (fetch all responses for a template without joining through question).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private Template template;

    @Column(name = "answer_key", nullable = false)
    private String answerKey;

    // Set when the response is about a specific family member rather than the proband.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "relationship_instance_id")
    private FamilyMember relationshipInstance;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "answer_type", nullable = false)
    private AnswerType answerType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "answer_value", nullable = false, columnDefinition = "jsonb")
    private String answerValue;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
