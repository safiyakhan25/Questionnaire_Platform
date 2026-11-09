package com.questionnaire.questionnaire_platform.repository;

import com.questionnaire.questionnaire_platform.entity.QuestionResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuestionResponseRepository extends JpaRepository<QuestionResponse, UUID> {

    List<QuestionResponse> findAllBySessionIdOrderByCreatedAtAsc(UUID sessionId);

    Optional<QuestionResponse> findTopBySessionIdAndQuestionIdOrderByCreatedAtDesc(UUID sessionId, UUID questionId);

    // relationshipInstanceId null = the proband's own answer. Needed once a template can be shared
    // across subjects (SubjectScope.BOTH): without this, a family member's pass would prefill from
    // the proband's (or a different relative's) answer to the same question.
    Optional<QuestionResponse> findTopBySessionIdAndQuestionIdAndRelationshipInstanceIdOrderByCreatedAtDesc(
            UUID sessionId, UUID questionId, UUID relationshipInstanceId);

    boolean existsByRelationshipInstanceId(UUID relationshipInstanceId);

    // FR-8.1: the active subject's actual answered path, grouped by template, ordered by when
    // each template was first answered - never display_order, never recalculated from current
    // rules. relationshipInstanceId null-aware (JPQL "=" never matches NULL, so both branches are
    // needed explicitly, unlike a Spring Data derived method which auto-handles a null parameter).
    @Query("""
            select new com.questionnaire.questionnaire_platform.repository.VisitedTemplateRow(r.template.id, min(r.createdAt))
            from QuestionResponse r
            where r.session.id = :sessionId
              and (
                (:relationshipInstanceId is null and r.relationshipInstance is null)
                or r.relationshipInstance.id = :relationshipInstanceId
              )
            group by r.template.id
            order by min(r.createdAt) asc
            """)
    List<VisitedTemplateRow> findVisitedTemplates(
            @Param("sessionId") UUID sessionId, @Param("relationshipInstanceId") UUID relationshipInstanceId);
}
