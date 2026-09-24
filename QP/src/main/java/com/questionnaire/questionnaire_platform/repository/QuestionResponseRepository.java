package com.questionnaire.questionnaire_platform.repository;

import com.questionnaire.questionnaire_platform.entity.QuestionResponse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuestionResponseRepository extends JpaRepository<QuestionResponse, UUID> {

    List<QuestionResponse> findAllBySessionIdOrderByCreatedAtAsc(UUID sessionId);

    Optional<QuestionResponse> findTopBySessionIdAndQuestionIdOrderByCreatedAtDesc(UUID sessionId, UUID questionId);
}
