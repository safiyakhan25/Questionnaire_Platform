package com.questionnaire.questionnaire_platform.repository;

import com.questionnaire.questionnaire_platform.entity.Workflow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface WorkflowRepository extends JpaRepository<Workflow, UUID> {

    boolean existsByCode(String code);

    boolean existsByDisplayOrder(Integer displayOrder);

    boolean existsByDisplayOrderAndIdNot(Integer displayOrder, UUID id);

    List<Workflow> findAllByOrderByDisplayOrderAsc();

    // Template has no back-reference on Workflow by design (unidirectional @ManyToOne only),
    // so existence is checked via a direct query instead of a collection association.
    @Query("select count(t) > 0 from Template t where t.workflow.id = :workflowId")
    boolean hasTemplates(@Param("workflowId") UUID workflowId);
}
