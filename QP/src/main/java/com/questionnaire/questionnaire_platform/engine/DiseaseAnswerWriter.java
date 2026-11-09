package com.questionnaire.questionnaire_platform.engine;

import com.questionnaire.questionnaire_platform.entity.Disease;
import com.questionnaire.questionnaire_platform.entity.DiseaseCatalog;
import com.questionnaire.questionnaire_platform.entity.DiseaseCodeSystemType;
import com.questionnaire.questionnaire_platform.entity.DiseaseStatusType;
import com.questionnaire.questionnaire_platform.entity.FamilyMember;
import com.questionnaire.questionnaire_platform.entity.Person;
import com.questionnaire.questionnaire_platform.exception.InvalidAnswerException;
import com.questionnaire.questionnaire_platform.repository.DiseaseCatalogRepository;
import com.questionnaire.questionnaire_platform.repository.DiseaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeType;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// FR-3.2: unlike PersonAnswerWriter/FamilyMemberAnswerWriter (one answer_key -> one fixed
// column, overwritten in place), a diagnosis is a repeatable sub-record - a subject can report
// more than one disease. The "diseases" answer_key's value is the COMPLETE current list of that
// subject's diagnoses as a JSON array (each person/family-member has exactly one such question in
// this design); every submission fully reconciles - existing DISEASE rows for the subject are
// replaced with exactly what's in the array, which is what makes an edit (change one field) and a
// clear (submit an empty array) both work through the same mechanism as every other answer.
//
// Patients report a disease by free-text name, not an ontology code - there's no catalog-picker
// question type yet (QP-Pedigree-Schema-Design.md §2.4/§6.3 leaves this open). Until one exists,
// a DISEASE_CATALOG entry is found-or-created by that name, coded CUSTOM/<name> and marked REPORTED
// (the schema's own vocabulary for "self-reported by family member", not pathology-verified) -
// using the schema's existing escape hatches rather than inventing a parallel one.
@Component
@RequiredArgsConstructor
public class DiseaseAnswerWriter {

    public static final String ANSWER_KEY = "diseases";

    private final DiseaseRepository diseaseRepository;
    private final DiseaseCatalogRepository diseaseCatalogRepository;

    public void applyToPerson(Person person, JsonNode value) {
        List<DiseaseEntry> entries = parseEntries(value);
        diseaseRepository.deleteAllByPersonId(person.getId());
        for (DiseaseEntry entry : entries) {
            Disease disease = new Disease();
            disease.setPerson(person);
            populate(disease, entry);
            diseaseRepository.save(disease);
        }
    }

    public void applyToFamilyMember(FamilyMember familyMember, JsonNode value) {
        List<DiseaseEntry> entries = parseEntries(value);
        diseaseRepository.deleteAllByFamilyMemberId(familyMember.getId());
        for (DiseaseEntry entry : entries) {
            Disease disease = new Disease();
            disease.setFamilyMember(familyMember);
            populate(disease, entry);
            diseaseRepository.save(disease);
        }
    }

    private void populate(Disease disease, DiseaseEntry entry) {
        disease.setDiseaseCatalog(findOrCreateCatalogEntry(entry.diseaseType()));
        disease.setCodeSystem(DiseaseCodeSystemType.CUSTOM);
        disease.setCodeId(entry.diseaseType());
        disease.setCodeLabel(entry.diseaseType());
        disease.setAgeAtDiagnosis(entry.ageAtDiagnosis());
        disease.setStatus(DiseaseStatusType.REPORTED);
    }

    private DiseaseCatalog findOrCreateCatalogEntry(String name) {
        return diseaseCatalogRepository.findByName(name).orElseGet(() -> {
            LocalDateTime now = LocalDateTime.now();
            DiseaseCatalog catalog = new DiseaseCatalog();
            catalog.setName(name);
            catalog.setCreatedAt(now);
            catalog.setUpdatedAt(now);
            return diseaseCatalogRepository.save(catalog);
        });
    }

    private record DiseaseEntry(String diseaseType, Integer ageAtDiagnosis) {
    }

    // null/empty array both mean "no diseases reported" - the same shape a cleared/nullified
    // answer takes (see AssessmentService's FR-9 cascade), so clearing just reconciles to zero rows.
    private List<DiseaseEntry> parseEntries(JsonNode value) {
        if (value == null || value.isNull()) {
            return List.of();
        }
        if (value.getNodeType() != JsonNodeType.ARRAY) {
            throw new InvalidAnswerException("'" + ANSWER_KEY + "' must be a list of disease entries");
        }
        List<DiseaseEntry> entries = new ArrayList<>();
        for (JsonNode entry : value) {
            boolean hasDiseaseType = entry.getNodeType() == JsonNodeType.OBJECT
                    && entry.has("diseaseType") && entry.get("diseaseType").isString()
                    && !entry.get("diseaseType").asString().isBlank();
            if (!hasDiseaseType) {
                throw new InvalidAnswerException(
                        "Each entry in '" + ANSWER_KEY + "' requires a non-blank diseaseType");
            }
            String diseaseType = entry.get("diseaseType").asString();
            Integer ageAtDiagnosis = entry.has("ageAtDiagnosis") && !entry.get("ageAtDiagnosis").isNull()
                    ? entry.get("ageAtDiagnosis").asInt() : null;
            entries.add(new DiseaseEntry(diseaseType, ageAtDiagnosis));
        }
        return entries;
    }
}
