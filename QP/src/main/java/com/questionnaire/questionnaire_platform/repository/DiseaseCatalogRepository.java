package com.questionnaire.questionnaire_platform.repository;

import com.questionnaire.questionnaire_platform.entity.DiseaseCatalog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DiseaseCatalogRepository extends JpaRepository<DiseaseCatalog, UUID> {

    Optional<DiseaseCatalog> findByName(String name);
}
