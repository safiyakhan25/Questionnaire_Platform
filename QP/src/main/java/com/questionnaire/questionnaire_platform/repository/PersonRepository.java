package com.questionnaire.questionnaire_platform.repository;

import com.questionnaire.questionnaire_platform.entity.Person;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PersonRepository extends JpaRepository<Person, UUID> {
}
