package com.questionnaire.questionnaire_platform.dto;

// FR-13.2/13.3: compares a declared relative count (Person.additionalFacts, read-only here - no
// question collects it yet) against how many FAMILY_MEMBER rows of that group actually exist.
// Read-only report, not an enforced limit - declaredCount null means nothing was ever declared,
// which is not treated as a mismatch.
public record RelativeCountReconciliation(
        String group,
        String declaredCountFactKey,
        Integer declaredCount,
        long actualCount,
        boolean matches
) {
}
