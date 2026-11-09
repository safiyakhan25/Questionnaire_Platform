package com.questionnaire.questionnaire_platform.service;

import com.questionnaire.questionnaire_platform.entity.FamilyMember;
import com.questionnaire.questionnaire_platform.entity.Person;
import com.questionnaire.questionnaire_platform.entity.QuestionnaireSession;
import com.questionnaire.questionnaire_platform.exception.ResourceNotFoundException;
import com.questionnaire.questionnaire_platform.repository.FamilyMemberRepository;
import com.questionnaire.questionnaire_platform.repository.QuestionnaireSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// Shared by every relationship-table service: resolves an individual id to either the session's
// own proband or one of their existing family members, scoped so one session can never reference
// another's data. Reused wherever a relationship edge needs "this endpoint is the proband or a
// family member" - parent/child here, person A/B in PARTNER_RELATIONSHIP, and any future
// TWINSHIP/ADOPTION/etc. table that needs the same XOR idiom.
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IndividualResolver {

    private final QuestionnaireSessionRepository sessionRepository;
    private final FamilyMemberRepository familyMemberRepository;

    public IndividualRef resolve(UUID sessionId, UUID individualId) {
        Person proband = findSessionOrThrow(sessionId).getPerson();
        if (individualId.equals(proband.getId())) {
            return new IndividualRef(proband, null);
        }
        FamilyMember familyMember = familyMemberRepository.findByIdAndProbandId(individualId, proband.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Individual " + individualId + " is neither the proband nor a family member of session " + sessionId));
        return new IndividualRef(null, familyMember);
    }

    private QuestionnaireSession findSessionOrThrow(UUID sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session " + sessionId + " not found"));
    }
}
