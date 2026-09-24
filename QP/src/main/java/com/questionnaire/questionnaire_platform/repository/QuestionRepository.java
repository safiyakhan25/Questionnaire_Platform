package com.questionnaire.questionnaire_platform.repository;

import com.questionnaire.questionnaire_platform.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface QuestionRepository extends JpaRepository<Question, UUID> {

    boolean existsByAnswerKey(String answerKey);

    boolean existsByTemplateIdAndDisplayOrder(UUID templateId, Integer displayOrder);

    boolean existsByTemplateIdAndDisplayOrderAndIdNot(UUID templateId, Integer displayOrder, UUID id);

    List<Question> findAllByTemplateIdOrderByDisplayOrderAsc(UUID templateId);

    // QuestionResponse has no back-reference on Question by design (unidirectional @ManyToOne
    // only), so existence is checked via a direct query instead of a collection association.
    @Query("select count(r) > 0 from QuestionResponse r where r.question.id = :questionId")
    boolean hasResponses(@Param("questionId") UUID questionId);
}
