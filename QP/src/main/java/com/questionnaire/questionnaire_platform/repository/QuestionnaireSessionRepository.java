package com.questionnaire.questionnaire_platform.repository;

import com.questionnaire.questionnaire_platform.entity.QuestionnaireSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface QuestionnaireSessionRepository extends JpaRepository<QuestionnaireSession, UUID> {

    List<QuestionnaireSession> findAllByOrderByStartedAtDesc();
}
