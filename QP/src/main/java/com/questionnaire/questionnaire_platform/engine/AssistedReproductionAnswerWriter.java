package com.questionnaire.questionnaire_platform.engine;

import com.questionnaire.questionnaire_platform.entity.AssistedReproduction;
import com.questionnaire.questionnaire_platform.entity.AssistedReproductionParticipant;
import com.questionnaire.questionnaire_platform.entity.AssistedReproductionParticipantRole;
import com.questionnaire.questionnaire_platform.entity.AssistedReproductionType;
import com.questionnaire.questionnaire_platform.entity.FamilyMember;
import com.questionnaire.questionnaire_platform.entity.Person;
import com.questionnaire.questionnaire_platform.exception.InvalidAnswerException;
import com.questionnaire.questionnaire_platform.repository.AssistedReproductionParticipantRepository;
import com.questionnaire.questionnaire_platform.repository.AssistedReproductionRepository;
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

// Maps to individual.assistedReproduction (pedigree_schema.json). Flattens the schema's six
// single/array participant fields (eggDonorId, spermDonorId, gestationalCarrierId,
// intendedParents[], donatedTo[], carriedFor[]) into one role-tagged participants[] list, per
// AssistedReproductionParticipant.java. Value shape: null (not applicable), or
// {"reproductionType": "IVF", "anonymous": false,
//  "participants": [{"role": "EGG_DONOR", "familyMemberId": "<uuid>"}, ...]}.
@Component
@RequiredArgsConstructor
public class AssistedReproductionAnswerWriter {

    public static final String ANSWER_KEY = "assisted_reproduction";

    private final AssistedReproductionRepository assistedReproductionRepository;
    private final AssistedReproductionParticipantRepository participantRepository;
    private final FamilyMemberRepository familyMemberRepository;

    public void applyToPerson(Person person, JsonNode value) {
        removeExisting(assistedReproductionRepository.findByIndividualPersonId(person.getId()));
        parseEntry(value).ifPresent(entry -> create(person, null, entry));
    }

    public void applyToFamilyMember(FamilyMember familyMember, JsonNode value) {
        removeExisting(assistedReproductionRepository.findByIndividualFamilyMemberId(familyMember.getId()));
        parseEntry(value).ifPresent(entry -> create(null, familyMember, entry));
    }

    private void removeExisting(Optional<AssistedReproduction> existing) {
        existing.ifPresent(record -> {
            participantRepository.deleteAllByAssistedReproductionId(record.getId());
            assistedReproductionRepository.delete(record);
        });
    }

    private void create(Person individualPerson, FamilyMember individualFamilyMember, Entry entry) {
        AssistedReproduction record = new AssistedReproduction();
        Person proband = individualPerson != null ? individualPerson : individualFamilyMember.getProband();
        record.setProband(proband);
        record.setIndividualPerson(individualPerson);
        record.setIndividualFamilyMember(individualFamilyMember);
        record.setReproductionType(entry.reproductionType());
        record.setAnonymous(entry.anonymous());
        record.setCreatedAt(LocalDateTime.now());
        record = assistedReproductionRepository.save(record);

        for (ParticipantEntry participantEntry : entry.participants()) {
            FamilyMember participant = familyMemberRepository.findById(participantEntry.familyMemberId())
                    .orElseThrow(() -> new InvalidAnswerException(
                            "'" + ANSWER_KEY + "' references an unknown family member: " + participantEntry.familyMemberId()));
            AssistedReproductionParticipant row = new AssistedReproductionParticipant();
            row.setAssistedReproduction(record);
            row.setParticipantRole(participantEntry.role());
            row.setParticipantFamilyMember(participant);
            participantRepository.save(row);
        }
    }

    private record ParticipantEntry(AssistedReproductionParticipantRole role, UUID familyMemberId) {
    }

    private record Entry(AssistedReproductionType reproductionType, Boolean anonymous, List<ParticipantEntry> participants) {
    }

    private Optional<Entry> parseEntry(JsonNode value) {
        if (value == null || value.isNull()) {
            return Optional.empty();
        }
        if (value.getNodeType() != JsonNodeType.OBJECT) {
            throw new InvalidAnswerException("'" + ANSWER_KEY + "' must be an object or null");
        }
        if (!value.has("reproductionType") || !value.get("reproductionType").isString()) {
            throw new InvalidAnswerException("'" + ANSWER_KEY + "' requires reproductionType");
        }
        AssistedReproductionType reproductionType;
        try {
            reproductionType = AssistedReproductionType.valueOf(value.get("reproductionType").asString());
        } catch (IllegalArgumentException e) {
            throw new InvalidAnswerException(
                    "'" + ANSWER_KEY + "' has an invalid reproductionType: " + value.get("reproductionType").asString());
        }
        Boolean anonymous = value.has("anonymous") && value.get("anonymous").isBoolean()
                ? value.get("anonymous").asBoolean() : null;

        List<ParticipantEntry> participants = new ArrayList<>();
        if (value.has("participants") && value.get("participants").getNodeType() == JsonNodeType.ARRAY) {
            for (JsonNode participantNode : value.get("participants")) {
                boolean hasRole = participantNode.has("role") && participantNode.get("role").isString();
                boolean hasId = participantNode.has("familyMemberId") && participantNode.get("familyMemberId").isString();
                if (!hasRole || !hasId) {
                    throw new InvalidAnswerException("Each entry in '" + ANSWER_KEY + "'.participants requires role and familyMemberId");
                }
                AssistedReproductionParticipantRole role;
                try {
                    role = AssistedReproductionParticipantRole.valueOf(participantNode.get("role").asString());
                } catch (IllegalArgumentException e) {
                    throw new InvalidAnswerException(
                            "'" + ANSWER_KEY + "'.participants has an invalid role: " + participantNode.get("role").asString());
                }
                participants.add(new ParticipantEntry(role, UUID.fromString(participantNode.get("familyMemberId").asString())));
            }
        }
        return Optional.of(new Entry(reproductionType, anonymous, participants));
    }
}
