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
@Table(name = "questionnaire_session")
@Getter
@Setter
@NoArgsConstructor
public class QuestionnaireSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "session_id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id", nullable = false)
    private Person person;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    private SessionStatus status;

    // Where this session currently is, for resume. Null until the Workflow Engine resolves the first template.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "previous_template_id")
    private Template previousTemplate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "previous_workflow_id")
    private Workflow previousWorkflow;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
