package com.questionnaire.questionnaire_platform.engine;

import com.questionnaire.questionnaire_platform.entity.FamilyMember;
import com.questionnaire.questionnaire_platform.entity.Person;
import com.questionnaire.questionnaire_platform.entity.TwinType;
import com.questionnaire.questionnaire_platform.entity.Twinship;
import com.questionnaire.questionnaire_platform.entity.TwinshipMember;
import com.questionnaire.questionnaire_platform.exception.InvalidAnswerException;
import com.questionnaire.questionnaire_platform.repository.FamilyMemberRepository;
import com.questionnaire.questionnaire_platform.repository.TwinshipMemberRepository;
import com.questionnaire.questionnaire_platform.repository.TwinshipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeType;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// Maps to individual.twinship (pedigree_schema.json) - a twin GROUP, not a pairwise link (see
// Twinship.java). Value shape: null (not part of a twin group), or
// {"twinType": "MONOZYGOTIC", "coTwinFamilyMemberIds": ["<uuid>", ...]}. coTwinFamilyMemberIds are
// resolved directly against FamilyMemberRepository - unlike FamilyMemberService.create's scopeId,
// this does NOT validate they belong to the same session's proband; that scoping gap is a known
// limitation of this first pass, not a deliberate design choice.
@Component
@RequiredArgsConstructor
public class TwinshipAnswerWriter {

    public static final String ANSWER_KEY = "twinship";

    private final TwinshipRepository twinshipRepository;
    private final TwinshipMemberRepository twinshipMemberRepository;
    private final FamilyMemberRepository familyMemberRepository;

    public void applyToPerson(Person person, JsonNode value) {
        removeFromExistingGroup(twinshipMemberRepository.findByPersonId(person.getId()));
        parseEntry(value).ifPresent(entry -> joinOrCreateGroup(person, null, entry));
    }

    public void applyToFamilyMember(FamilyMember familyMember, JsonNode value) {
        removeFromExistingGroup(twinshipMemberRepository.findByFamilyMemberId(familyMember.getId()));
        parseEntry(value).ifPresent(entry -> joinOrCreateGroup(null, familyMember, entry));
    }

    // Removes the subject from whatever group it was in, dissolving the group entirely if fewer
    // than 2 members would remain (a "twin group" of 0 or 1 is meaningless).
    private void removeFromExistingGroup(Optional<TwinshipMember> existing) {
        if (existing.isEmpty()) {
            return;
        }
        UUID twinshipId = existing.get().getTwinship().getId();
        twinshipMemberRepository.delete(existing.get());
        List<TwinshipMember> remaining = twinshipMemberRepository.findAllByTwinshipId(twinshipId);
        if (remaining.size() < 2) {
            twinshipMemberRepository.deleteAll(remaining);
            twinshipRepository.deleteById(twinshipId);
        }
    }

    private void joinOrCreateGroup(Person subjectPerson, FamilyMember subjectFamilyMember, Entry entry) {
        Twinship twinship = findExistingGroupAmong(entry.coTwinFamilyMemberIds())
                .orElseGet(() -> {
                    Person proband = subjectPerson != null ? subjectPerson : subjectFamilyMember.getProband();
                    Twinship created = new Twinship();
                    created.setProband(proband);
                    created.setTwinType(entry.twinType());
                    created.setCreatedAt(LocalDateTime.now());
                    return twinshipRepository.save(created);
                });
        twinship.setTwinType(entry.twinType());
        twinshipRepository.save(twinship);

        addMemberIfMissing(twinship, subjectPerson, subjectFamilyMember);
        for (UUID coTwinId : entry.coTwinFamilyMemberIds()) {
            FamilyMember coTwin = familyMemberRepository.findById(coTwinId)
                    .orElseThrow(() -> new InvalidAnswerException(
                            "'" + ANSWER_KEY + "' references an unknown family member: " + coTwinId));
            addMemberIfMissing(twinship, null, coTwin);
        }
    }

    private Optional<Twinship> findExistingGroupAmong(List<UUID> coTwinFamilyMemberIds) {
        for (UUID coTwinId : coTwinFamilyMemberIds) {
            Optional<TwinshipMember> existing = twinshipMemberRepository.findByFamilyMemberId(coTwinId);
            if (existing.isPresent()) {
                return Optional.of(existing.get().getTwinship());
            }
        }
        return Optional.empty();
    }

    private void addMemberIfMissing(Twinship twinship, Person person, FamilyMember familyMember) {
        boolean alreadyMember = person != null
                ? twinshipMemberRepository.findByPersonId(person.getId()).isPresent()
                : twinshipMemberRepository.findByFamilyMemberId(familyMember.getId()).isPresent();
        if (alreadyMember) {
            return;
        }
        TwinshipMember member = new TwinshipMember();
        member.setTwinship(twinship);
        member.setPerson(person);
        member.setFamilyMember(familyMember);
        twinshipMemberRepository.save(member);
    }

    private record Entry(TwinType twinType, List<UUID> coTwinFamilyMemberIds) {
    }

    private Optional<Entry> parseEntry(JsonNode value) {
        if (value == null || value.isNull()) {
            return Optional.empty();
        }
        if (value.getNodeType() != JsonNodeType.OBJECT) {
            throw new InvalidAnswerException("'" + ANSWER_KEY + "' must be an object or null");
        }
        if (!value.has("twinType") || !value.get("twinType").isString()) {
            throw new InvalidAnswerException("'" + ANSWER_KEY + "' requires twinType");
        }
        TwinType twinType;
        try {
            twinType = TwinType.valueOf(value.get("twinType").asString());
        } catch (IllegalArgumentException e) {
            throw new InvalidAnswerException("'" + ANSWER_KEY + "' has an invalid twinType: " + value.get("twinType").asString());
        }
        List<UUID> coTwinIds = new ArrayList<>();
        if (value.has("coTwinFamilyMemberIds") && value.get("coTwinFamilyMemberIds").getNodeType() == JsonNodeType.ARRAY) {
            for (JsonNode idNode : value.get("coTwinFamilyMemberIds")) {
                coTwinIds.add(UUID.fromString(idNode.asString()));
            }
        }
        if (coTwinIds.isEmpty()) {
            throw new InvalidAnswerException("'" + ANSWER_KEY + "' requires at least one coTwinFamilyMemberIds entry");
        }
        return Optional.of(new Entry(twinType, coTwinIds));
    }
}
