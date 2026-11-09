package com.questionnaire.questionnaire_platform.service;

import com.questionnaire.questionnaire_platform.entity.FamilyMember;
import com.questionnaire.questionnaire_platform.entity.Person;

import java.util.UUID;

// Exactly one of person/familyMember is set - the same XOR idiom DISEASE/MUTATION already use for
// ownership, here used to say "this individual, wherever they appear in a pedigree, is either the
// proband or one of their family members." Resolved by IndividualResolver.
public record IndividualRef(Person person, FamilyMember familyMember) {

    public UUID id() {
        return person != null ? person.getId() : familyMember.getId();
    }
}
