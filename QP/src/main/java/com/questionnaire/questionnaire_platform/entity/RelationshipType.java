package com.questionnaire.questionnaire_platform.entity;

public enum RelationshipType {
    MOTHER, FATHER, BROTHER, SISTER, SON, DAUGHTER, SPOUSE,
    HALF_BROTHER, HALF_SISTER, ADOPTED_SON, ADOPTED_DAUGHTER,
    GRANDFATHER, GRANDMOTHER, UNCLE, AUNT, COUSIN, NIECE, NEPHEW,
    // A pregnancy loss or current pregnancy before sex is known - doesn't fit SON/DAUGHTER. The
    // FAMILY_MEMBER row this creates is what a PREGNANCY_OUTCOME record attaches to.
    PREGNANCY_LOSS
}
