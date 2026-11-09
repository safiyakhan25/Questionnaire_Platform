# QP Pedigree Data Model — Final

## PERSON
| Column | Type | Key |
|---|---|---|
| id | UUID | PK |
| is_active | boolean | NOT NULL |
| first_name, middle_name, last_name, preferred_name | varchar | |
| sex_assigned_at_birth | enum | |
| gender_identity | enum | |
| is_intersex | boolean | |
| country_of_origin | text[] | |
| birth_date | timestamp | |
| marital_status, communication_language, blood_type, primary_contact | varchar | |
| race, ethnicity | text[] | |
| deceased | boolean | |
| deceased_date_time | timestamp | |
| age | integer | |
| contact, address, additional_facts | jsonb | |
| created_at, updated_at | timestamp | NOT NULL |

## FAMILY_MEMBER
| Column | Type | Key |
|---|---|---|
| id | UUID | PK |
| proband_id | UUID | FK → PERSON(id), NOT NULL |
| maternal_paternal_side | enum | NOT NULL |
| first_name, middle_name, last_name, preferred_name | varchar | |
| sex_assigned_at_birth | enum | |
| gender_identity | enum | |
| is_intersex | boolean | |
| country_of_origin | text[] | |
| birth_date | timestamp | |
| marital_status, communication_language, blood_type, primary_contact | varchar | |
| race, ethnicity | text[] | |
| deceased | boolean | |
| deceased_date_time | timestamp | |
| age | integer | |
| contact, address, additional_facts | jsonb | |
| created_at, updated_at | timestamp | NOT NULL |

## FAMILY_MEMBER_ROLE
| Column | Type | Key |
|---|---|---|
| id | UUID | PK |
| family_member_id | UUID | FK → FAMILY_MEMBER(id), NOT NULL |
| scope_person_id | UUID | FK → PERSON(id), XOR |
| scope_family_member_id | UUID | FK → FAMILY_MEMBER(id), XOR |
| relationship_type | enum | NOT NULL |
| created_at | timestamp | NOT NULL |

## PARENT_CHILD_RELATIONSHIP
| Column | Type | Key |
|---|---|---|
| id | UUID | PK |
| proband_id | UUID | FK → PERSON(id), NOT NULL |
| parent_person_id | UUID | FK → PERSON(id), XOR |
| parent_family_member_id | UUID | FK → FAMILY_MEMBER(id), XOR |
| child_family_member_id | UUID | FK → FAMILY_MEMBER(id), NOT NULL |
| created_at | timestamp | NOT NULL |

## PARTNER_RELATIONSHIP
| Column | Type | Key |
|---|---|---|
| id | UUID | PK |
| proband_id | UUID | FK → PERSON(id), NOT NULL |
| person_a_person_id | UUID | FK → PERSON(id), XOR |
| person_a_family_member_id | UUID | FK → FAMILY_MEMBER(id), XOR |
| person_b_person_id | UUID | FK → PERSON(id), XOR |
| person_b_family_member_id | UUID | FK → FAMILY_MEMBER(id), XOR |
| mating_line_type | enum | NOT NULL |
| reproductive_status | enum | NOT NULL |
| relationship_status | enum | |
| consanguineous | boolean | |
| unshown_children_count | integer | |
| ended_year | integer | |
| consanguinity_degree | varchar | |
| created_at | timestamp | NOT NULL |

## DISEASE_CATALOG
| Column | Type | Key |
|---|---|---|
| id | UUID | PK |
| name | varchar | NOT NULL, UNIQUE |
| abbreviation | varchar | |
| default_color | varchar | |
| created_at, updated_at | timestamp | NOT NULL |

## DISEASE
| Column | Type | Key |
|---|---|---|
| disease_id | UUID | PK |
| person_id | UUID | FK → PERSON(id), XOR |
| family_member_id | UUID | FK → FAMILY_MEMBER(id), XOR |
| disease_catalog_id | UUID | FK → DISEASE_CATALOG(id), NOT NULL |
| code_system | enum | NOT NULL |
| code_id | varchar | NOT NULL |
| code_label | varchar | |
| age_at_diagnosis | integer | |
| laterality | enum | |
| status | enum | NOT NULL |

## MUTATION
| Column | Type | Key |
|---|---|---|
| mutation_id | UUID | PK |
| person_id | UUID | FK → PERSON(id), XOR |
| family_member_id | UUID | FK → FAMILY_MEMBER(id), XOR |
| gene | varchar | NOT NULL |
| result | enum | NOT NULL |
| variant | varchar | |
| test_type | enum | |

## GENDER_TRANSITION
| Column | Type | Key |
|---|---|---|
| id | UUID | PK |
| person_id | UUID | FK → PERSON(id), XOR, UNIQUE per individual |
| family_member_id | UUID | FK → FAMILY_MEMBER(id), XOR, UNIQUE per individual |
| from_gender | enum | NOT NULL |
| to_gender | enum | NOT NULL |
| transition_year | integer | |
| created_at | timestamp | NOT NULL |

## TWINSHIP
| Column | Type | Key |
|---|---|---|
| id | UUID | PK |
| proband_id | UUID | FK → PERSON(id), NOT NULL |
| twin_type | enum | NOT NULL |
| created_at | timestamp | NOT NULL |

## TWINSHIP_MEMBER
| Column | Type | Key |
|---|---|---|
| id | UUID | PK |
| twinship_id | UUID | FK → TWINSHIP(id), NOT NULL |
| person_id | UUID | FK → PERSON(id), XOR |
| family_member_id | UUID | FK → FAMILY_MEMBER(id), XOR |

## ADOPTION
| Column | Type | Key |
|---|---|---|
| id | UUID | PK |
| proband_id | UUID | FK → PERSON(id), NOT NULL |
| individual_person_id | UUID | FK → PERSON(id), XOR |
| individual_family_member_id | UUID | FK → FAMILY_MEMBER(id), XOR |
| adoption_type | enum | NOT NULL |
| created_at | timestamp | NOT NULL |

## ADOPTION_PARENT
| Column | Type | Key |
|---|---|---|
| id | UUID | PK |
| adoption_id | UUID | FK → ADOPTION(id), NOT NULL |
| parent_person_id | UUID | FK → PERSON(id), XOR |
| parent_family_member_id | UUID | FK → FAMILY_MEMBER(id), XOR |

## PREGNANCY_OUTCOME
| Column | Type | Key |
|---|---|---|
| id | UUID | PK |
| family_member_id | UUID | FK → FAMILY_MEMBER(id), NOT NULL, UNIQUE |
| outcome_type | enum | NOT NULL |
| gestational_weeks | integer | |
| lmp_date | date | |
| created_at | timestamp | NOT NULL |

## ASSISTED_REPRODUCTION
| Column | Type | Key |
|---|---|---|
| id | UUID | PK |
| proband_id | UUID | FK → PERSON(id), NOT NULL |
| individual_person_id | UUID | FK → PERSON(id), XOR |
| individual_family_member_id | UUID | FK → FAMILY_MEMBER(id), XOR |
| reproduction_type | enum | NOT NULL |
| anonymous | boolean | |
| created_at | timestamp | NOT NULL |

## ASSISTED_REPRODUCTION_PARTICIPANT
| Column | Type | Key |
|---|---|---|
| id | UUID | PK |
| assisted_reproduction_id | UUID | FK → ASSISTED_REPRODUCTION(id), NOT NULL |
| participant_role | enum | NOT NULL |
| participant_person_id | UUID | FK → PERSON(id), XOR |
| participant_family_member_id | UUID | FK → FAMILY_MEMBER(id), XOR |
