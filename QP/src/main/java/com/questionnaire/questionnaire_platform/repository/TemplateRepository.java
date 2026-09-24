package com.questionnaire.questionnaire_platform.repository;

import com.questionnaire.questionnaire_platform.entity.Template;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface TemplateRepository extends JpaRepository<Template, UUID> {

    boolean existsByCode(String code);

    boolean existsByWorkflowIdAndDisplayOrder(UUID workflowId, Integer displayOrder);

    boolean existsByWorkflowIdAndDisplayOrderAndIdNot(UUID workflowId, Integer displayOrder, UUID id);

    List<Template> findAllByWorkflowIdOrderByDisplayOrderAsc(UUID workflowId);

    // Question has no back-reference on Template by design (unidirectional @ManyToOne only),
    // so existence is checked via a direct query instead of a collection association.
    @Query("select count(q) > 0 from Question q where q.template.id = :templateId")
    boolean hasQuestions(@Param("templateId") UUID templateId);
}
