package com.questionnaire.questionnaire_platform.engine;

import com.questionnaire.questionnaire_platform.entity.Adoption;
import com.questionnaire.questionnaire_platform.entity.AdoptionParent;
import com.questionnaire.questionnaire_platform.entity.AdoptionType;
import com.questionnaire.questionnaire_platform.entity.FamilyMember;
import com.questionnaire.questionnaire_platform.entity.Person;
import com.questionnaire.questionnaire_platform.exception.InvalidAnswerException;
import com.questionnaire.questionnaire_platform.repository.AdoptionParentRepository;
import com.questionnaire.questionnaire_platform.repository.AdoptionRepository;
import com.questionnaire.questionnaire_platform.repository.FamilyMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeType;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// Maps to individual.adoption (pedigree_schema.json). Adoptive parents only - biological parents
// are derived at export time from the individual's existing PARENT_CHILD_RELATIONSHIP/
// FAMILY_MEMBER_ROLE data, per ADOPTION_PARENT's own design (see AdoptionParent.java). Value
// shape: null (not adopted), or {"adoptionType": "ADOPTED_IN"|"ADOPTED_OUT",
// "adoptiveParentFamilyMemberIds": ["<uuid>", ...]} - required and non-empty when adoptionType is
// ADOPTED_IN, per the schema's own conditional requirement. adoptiveParentFamilyMemberIds are
// resolved directly against FamilyMemberRepository, with the same session/ownership-scoping
// limitation noted on TwinshipAnswerWriter.
@Component
@RequiredArgsConstructor
public class AdoptionAnswerWriter {

    public static final String ANSWER_KEY = "adoption";

    private final AdoptionRepository adoptionRepository;
    private final AdoptionParentRepository adoptionParentRepository;
    private final FamilyMemberRepository familyMemberRepository;

    public void applyToPerson(Person person, JsonNode value) {
        removeExisting(adoptionRepository.findByIndividualPersonId(person.getId()));
        parseEntry(value).ifPresent(entry -> create(person, null, entry));
    }

    public void applyToFamilyMember(FamilyMember familyMember, JsonNode value) {
        removeExisting(adoptionRepository.findByIndividualFamilyMemberId(familyMember.getId()));
        parseEntry(value).ifPresent(entry -> create(null, familyMember, entry));
    }

    private void removeExisting(Optional<Adoption> existing) {
        existing.ifPresent(adoption -> {
            adoptionParentRepository.deleteAllByAdoptionId(adoption.getId());
            adoptionRepository.delete(adoption);
        });
    }

    private void create(Person individualPerson, FamilyMember individualFamilyMember, Entry entry) {
        Adoption adoption = new Adoption();
        Person proband = individualPerson != null ? individualPerson : individualFamilyMember.getProband();
        adoption.setProband(proband);
        adoption.setIndividualPerson(individualPerson);
        adoption.setIndividualFamilyMember(individualFamilyMember);
        adoption.setAdoptionType(entry.adoptionType());
        adoption.setCreatedAt(LocalDateTime.now());
        adoption = adoptionRepository.save(adoption);

        for (UUID parentId : entry.adoptiveParentFamilyMemberIds()) {
            FamilyMember parent = familyMemberRepository.findById(parentId)
                    .orElseThrow(() -> new InvalidAnswerException(
                            "'" + ANSWER_KEY + "' references an unknown family member: " + parentId));
            AdoptionParent adoptionParent = new AdoptionParent();
            adoptionParent.setAdoption(adoption);
            adoptionParent.setParentFamilyMember(parent);
            adoptionParentRepository.save(adoptionParent);
        }
    }

    private record Entry(AdoptionType adoptionType, List<UUID> adoptiveParentFamilyMemberIds) {
    }

    private Optional<Entry> parseEntry(JsonNode value) {
        if (value == null || value.isNull()) {
            return Optional.empty();
        }
        if (value.getNodeType() != JsonNodeType.OBJECT) {
            throw new InvalidAnswerException("'" + ANSWER_KEY + "' must be an object or null");
        }
        if (!value.has("adoptionType") || !value.get("adoptionType").isString()) {
            throw new InvalidAnswerException("'" + ANSWER_KEY + "' requires adoptionType");
        }
        AdoptionType adoptionType;
        try {
            adoptionType = AdoptionType.valueOf(value.get("adoptionType").asString());
        } catch (IllegalArgumentException e) {
            throw new InvalidAnswerException("'" + ANSWER_KEY + "' has an invalid adoptionType: " + value.get("adoptionType").asString());
        }
        List<UUID> adoptiveParentIds = new ArrayList<>();
        if (value.has("adoptiveParentFamilyMemberIds") && value.get("adoptiveParentFamilyMemberIds").getNodeType() == JsonNodeType.ARRAY) {
            for (JsonNode idNode : value.get("adoptiveParentFamilyMemberIds")) {
                adoptiveParentIds.add(UUID.fromString(idNode.asString()));
            }
        }
        if (adoptionType == AdoptionType.ADOPTED_IN && adoptiveParentIds.isEmpty()) {
            throw new InvalidAnswerException(
                    "'" + ANSWER_KEY + "' requires at least one adoptiveParentFamilyMemberIds entry when adoptionType is ADOPTED_IN");
        }
        return Optional.of(new Entry(adoptionType, adoptiveParentIds));
    }
}
