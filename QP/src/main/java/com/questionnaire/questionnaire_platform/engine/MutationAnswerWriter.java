package com.questionnaire.questionnaire_platform.engine;

import com.questionnaire.questionnaire_platform.entity.FamilyMember;
import com.questionnaire.questionnaire_platform.entity.Mutation;
import com.questionnaire.questionnaire_platform.entity.MutationResultType;
import com.questionnaire.questionnaire_platform.entity.Person;
import com.questionnaire.questionnaire_platform.exception.InvalidAnswerException;
import com.questionnaire.questionnaire_platform.repository.MutationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeType;

import java.util.ArrayList;
import java.util.List;

// Same reconciliation shape as DiseaseAnswerWriter, for genetic findings - see its header comment
// for the full reasoning (repeatable sub-record, full-array-replace semantics).
//
// Patients report a finding as free text, not a classified lab result - there's no structured
// result-entry question type yet. Until one exists, the free text is stored as `variant` and
// `result` defaults to PENDING (the schema's own vocabulary for "not yet classified"), rather than
// guessing a pathogenicity classification from unstructured patient text.
@Component
@RequiredArgsConstructor
public class MutationAnswerWriter {

    public static final String ANSWER_KEY = "genetic_findings";

    private final MutationRepository mutationRepository;

    public void applyToPerson(Person person, JsonNode value) {
        List<MutationEntry> entries = parseEntries(value);
        mutationRepository.deleteAllByPersonId(person.getId());
        for (MutationEntry entry : entries) {
            Mutation mutation = new Mutation();
            mutation.setPerson(person);
            populate(mutation, entry);
            mutationRepository.save(mutation);
        }
    }

    public void applyToFamilyMember(FamilyMember familyMember, JsonNode value) {
        List<MutationEntry> entries = parseEntries(value);
        mutationRepository.deleteAllByFamilyMemberId(familyMember.getId());
        for (MutationEntry entry : entries) {
            Mutation mutation = new Mutation();
            mutation.setFamilyMember(familyMember);
            populate(mutation, entry);
            mutationRepository.save(mutation);
        }
    }

    private void populate(Mutation mutation, MutationEntry entry) {
        mutation.setGene(entry.gene());
        mutation.setVariant(entry.mutation());
        mutation.setResult(MutationResultType.PENDING);
    }

    private record MutationEntry(String gene, String mutation) {
    }

    private List<MutationEntry> parseEntries(JsonNode value) {
        if (value == null || value.isNull()) {
            return List.of();
        }
        if (value.getNodeType() != JsonNodeType.ARRAY) {
            throw new InvalidAnswerException("'" + ANSWER_KEY + "' must be a list of genetic-finding entries");
        }
        List<MutationEntry> entries = new ArrayList<>();
        for (JsonNode entry : value) {
            boolean hasGene = entry.getNodeType() == JsonNodeType.OBJECT
                    && entry.has("gene") && entry.get("gene").isString() && !entry.get("gene").asString().isBlank();
            if (!hasGene) {
                throw new InvalidAnswerException("Each entry in '" + ANSWER_KEY + "' requires a non-blank gene");
            }
            String gene = entry.get("gene").asString();
            String mutationValue = entry.has("mutation") && entry.get("mutation").isString()
                    ? entry.get("mutation").asString() : null;
            entries.add(new MutationEntry(gene, mutationValue));
        }
        return entries;
    }
}
