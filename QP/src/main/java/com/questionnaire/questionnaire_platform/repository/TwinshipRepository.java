package com.questionnaire.questionnaire_platform.repository;

import com.questionnaire.questionnaire_platform.entity.Twinship;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TwinshipRepository extends JpaRepository<Twinship, UUID> {
}
