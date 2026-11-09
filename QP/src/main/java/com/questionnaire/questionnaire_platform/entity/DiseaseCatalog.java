package com.questionnaire.questionnaire_platform.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

// Global, admin-managed catalog of disease types - every DISEASE row references one of these.
// Separate from the pedigree export's own per-document diseases[] (<=4 "featured" entries with a
// rendering quadrant, assigned at export time, not stored here).
@Entity
@Table(name = "disease_catalog")
@Getter
@Setter
@NoArgsConstructor
public class DiseaseCatalog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name;

    private String abbreviation;

    @Column(name = "default_color")
    private String defaultColor;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
