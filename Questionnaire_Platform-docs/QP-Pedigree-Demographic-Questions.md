# Demographic & Relationship Question Set

| | |
|---|---|
| **Document status** | **Seeded** (2026-10-14) — 2 workflows, 6 templates, 42 questions, created via the admin APIs and live-verified end-to-end. This document now describes what actually exists, not a draft. |
| **Purpose** | A question set covering proband + family-member demographics, and the questions that *drive* establishing the parent/child and partner relationship graph, sufficient to eventually assemble a pedigree. |
| **Scope** | Demographics + relationships only, as asked. Disease/genetic-finding questions (the `diseases`/`genetic_findings` answer_keys, FR-3.2) aren't re-specified here. |
| **Revision** | v2 (2026-10-14) — incorporates a mentor review of v1, confirms the sex/gender migration (§3.2 of `QP-Pedigree-Schema-Design.md`) and the `PARTNER_RELATIONSHIP` consanguinity/relationship-status split, both implemented this pass. See §"What changed from v1" at the end. |

---

## Workflow → Template → Question structure

| Workflow | Template | SubjectScope | Who answers it |
|---|---|---|---|
| **Personal Details** | `DEMOGRAPHICS` | **BOTH** | The proband, and — reused as-is, unduplicated — every family member's own pass |
| **Family History** | `PARENTS` | PROBAND_ONLY | Proband only |
| | `SIBLINGS` | PROBAND_ONLY | Proband only |
| | `PARTNERS` | PROBAND_ONLY | Proband only |
| | `CHILDREN` | PROBAND_ONLY | Proband only |
| | `EXTENDED_FAMILY` | PROBAND_ONLY | Proband only |

Live-verified (not just reasoned about): the proband's session genuinely crosses from the `Personal Details` workflow into `Family History`; a family member's pass lands on the exact same `DEMOGRAPHICS` template row (never a duplicate) and is never offered any `Family History` template; control returns to the proband exactly where they left off afterward.

`FAMILY_MEMBER.relationshipType` is always defined *relative to the proband* — nothing derives it automatically from "whose pass is currently active." `EXTENDED_FAMILY` captures grandparents/aunts-uncles/cousins/nieces-nephews as direct proband-side declarations rather than recursing into another family member's own pass, since nothing computes "relative-to-proband" automatically.

**Honesty note on relationship-edge creation (corrected per mentor review):** answering a question never automatically creates a `PARENT_CHILD_RELATIONSHIP` or `PARTNER_RELATIONSHIP` row. Every edge needs an explicit, separate call to `POST .../relationships/parent-child` or `POST .../relationships/partners` after the relevant `FamilyMember` rows exist. The "Drives" column below states this honestly wherever it applies — no auto-linking orchestration exists, and none was built this pass (the user explicitly scoped this to "just get started," not a full orchestration layer).

**Section G needs backend work first** — it assumes `TWINSHIP`/`ADOPTION`/`PREGNANCY_OUTCOME`/`ASSISTED_REPRODUCTION`, which are designed (`QP-Pedigree-Schema-Design.md` §2.6–2.9) but not implemented. Still out of scope.

---

## Workflow: Personal Details → Template: `DEMOGRAPHICS` (SubjectScope: BOTH)

| # | answer_key | Question text | Type | Required | Notes |
|---|---|---|---|---|---|
| 1 | `first_name` | First name | TEXT | Yes | |
| 2 | `middle_name` | Middle name | TEXT | No | |
| 3 | `last_name` | Last name | TEXT | Yes | |
| 4 | `preferred_name` | What would you like to be called? | TEXT | No | |
| 5 | `birth_date` | Date of birth (if known) | DATE | No | `age` is the fallback when unknown |
| 6 | `age` | Approximate current age (if exact birth date isn't known) | NUMBER | No | Visible only when `birth_date` is empty |
| 7 | `sex_assigned_at_birth` | Sex assigned at birth | RADIO | Yes | `Person`/`FamilyMember.sexAssignedAtBirth`, enum `UNKNOWN`/`MALE`/`FEMALE` |
| 8 | `gender_identity` | Gender identity | RADIO | No | Structured enum `MAN`/`WOMAN`/`NONBINARY`/`UNKNOWN` (changed from free text) |
| 9 | `is_intersex` | Is this person intersex? | RADIO | No | Boolean, independent of the two fields above — intersex is annotation-only, never a value of either enum |
| 10 | `deceased` | Is this person living or deceased? | RADIO | **No** (corrected from v1's "Yes" — see below) | Living/Deceased; leaving it unanswered *is* the third "unknown" state |
| 11 | `age_at_death` | Age at death (if known) | NUMBER | No | Visible only when `deceased = true`. No dedicated column — lands in `additional_facts` |
| 12 | `cause_of_death` | Cause of death (if known) | TEXT | No | Same — `additional_facts`, not a fixed column |
| 13 | `ethnicity` | Ethnicity (select all that apply) | CHECKBOX | No | Options: Hispanic or Latino / Not Hispanic or Latino / Ashkenazi Jewish / Unknown |
| 14 | `race` | Race (select all that apply) | CHECKBOX | No | Options: White / Black or African American / Asian / American Indian or Alaska Native / Native Hawaiian or Other Pacific Islander / Other / Unknown |
| 15 | `country_of_origin` | Country of origin (ISO 3166-1 alpha-2 code, e.g. US, IN, GB) | **TEXT** (corrected from v1's CHECKBOX) | No | `validation.pattern: ^[A-Z]{2}$`. Changed from CHECKBOX because pattern validation isn't enforced for CHECKBOX questions (`AnswerValidator` only checks CHECKBOX values against a fixed `options` list) |

**Why `deceased` isn't required (corrected from v1):** `AnswerValidator.isBlank()` treats a submitted JSON `null` as "not answered," so a required question can't use `null` as a valid "Unknown" option value. Since the DB column is a nullable `Boolean` specifically to support a genuine 3-state unknown/true/false, "Unknown" has to mean *not answering it at all* — making it required would contradict that. Same reasoning applies throughout this set: nowhere is `null` used as an option value.

---

## Workflow: Family History → Template: `PARENTS`

| # | answer_key | Question text | Type | Required | Drives |
|---|---|---|---|---|---|
| 16 | `knows_biological_mother` | Do you know your biological mother? | RADIO (Yes/No/Unknown) | Yes | If Yes → `POST .../family-members {relationshipType: MOTHER}`, then her own `DEMOGRAPHICS` pass runs |
| 17 | `knows_biological_father` | Do you know your biological father? | RADIO (Yes/No/Unknown) | Yes | Same pattern, `relationshipType: FATHER` |
| 18 | `parents_biologically_related` | Are your biological parents related to each other (e.g. cousins)? | RADIO (Yes/No/Unknown) | Yes | If Yes, once both parents exist as family members → `POST .../relationships/partners {personAId: mother, personBId: father, consanguineous: true, consanguinityDegree}` — an explicit call, not automatic |
| 19 | `consanguinity_degree` | What is the relationship between your parents (e.g. first cousins)? | TEXT | No | Only visible if Q18 = Yes — maps to `PartnerRelationship.consanguinityDegree` |
| 20 | `parents_relationship_status` | What best describes your parents' relationship status? | RADIO (Married/Divorced/Separated/Never married/Widowed/Unknown) | No | Maps to `PartnerRelationship.relationshipStatus` — **not** `matingLineType` (see "What changed from v1") |

---

## Workflow: Family History → Template: `SIBLINGS`

| # | answer_key | Question text | Type | Required | Drives |
|---|---|---|---|---|---|
| 21 | `has_siblings` | Do you have any brothers or sisters? | RADIO (Yes/No) | Yes | Gates the rest |
| 22 | `sibling_count` | How many siblings do you have? | NUMBER | No | Wired to FR-13.2/13.3 reconciliation — compared against actual `BROTHER`/`SISTER`/`HALF_BROTHER`/`HALF_SISTER` count. Live-verified: declaring 2 with 0 actual family members correctly reports `matches: false`. |
| 23 | `sibling_relationship_type` *(asked once per sibling, driven by the client)* | What is this sibling's relationship to you? | RADIO (Full brother/Full sister/Half-brother/Half-sister) | No | Selects `relationshipType` at `POST .../family-members` time (client also supplies `maternalPaternalSide`, already a required field on that call). **Corrected from v1**: linking the sibling to an existing parent needs an explicit separate `POST .../relationships/parent-child` call per edge — not automatic. |

---

## Workflow: Family History → Template: `PARTNERS`

| # | answer_key | Question text | Type | Required | Drives |
|---|---|---|---|---|---|
| 24 | `has_partner` | Do you currently have a spouse or partner? | RADIO (Yes/No) | Yes | If Yes → `POST .../family-members {relationshipType: SPOUSE}` |
| 25 | `partner_relationship_status` | What best describes this relationship? | RADIO (Married/Divorced/Separated/Never married/Widowed/Unknown) | No | `PartnerRelationship.relationshipStatus` |
| 26 | `partner_biologically_related` | Are you and this partner biologically related (e.g. cousins)? | RADIO (Yes/No/Unknown) | No | `PartnerRelationship.consanguineous` |
| 27 | `partner_consanguinity_degree` | What is the relationship between you and this partner (e.g. first cousins)? | TEXT | No | Only visible if Q26 = Yes — `PartnerRelationship.consanguinityDegree` |
| 28 | `partner_reproductive_status` | Have you had children with this partner? | RADIO (7 options) | No | `PartnerRelationship.reproductiveStatus` |
| 29 | `unshown_children_count` | How many children (not otherwise listed) do you have with this partner? | NUMBER | No | Only visible if Q28 = "Has children not shown here" |
| 30 | `relationship_ended_year` | What year did this relationship end (if applicable)? | NUMBER | No | Only visible if Q25 is Separated or Divorced. Enforced server-side against `relationshipStatus` (not `matingLineType` — see below) |
| 31 | `has_previous_partners` | Have you had any other spouses or partners? | RADIO (Yes/No) | No | Client re-runs Q24–30 for each additional partner — a client-driven loop, not a backend one |

**Replaces v1's single `partner_relationship_type` question** (which tried to express Married/Consanguineous/Separated/Divorced/No longer together/Casual-or-unknown as one value) **with three independent questions** (Q25, Q26, Q27) — see "What changed from v1."

---

## Workflow: Family History → Template: `CHILDREN`

| # | answer_key | Question text | Type | Required | Drives |
|---|---|---|---|---|---|
| 32 | `has_children` | Do you have any children? | RADIO (Yes/No) | Yes | Gates the rest |
| 33 | `children_count` | How many children do you have? | NUMBER | No | Wired to FR-13.2/13.3 reconciliation against `SON`/`DAUGHTER`/`ADOPTED_SON`/`ADOPTED_DAUGHTER` |
| 34 | `child_relationship_type` *(asked once per child)* | Is this your biological or adopted child? | RADIO (Biological son/Biological daughter/Adopted son/Adopted daughter) | No | Selects `relationshipType` at creation. **Corrected from v1**: the `PARENT_CHILD_RELATIONSHIP` edge to the proband is not created automatically — needs its own explicit API call |

---

## Workflow: Family History → Template: `EXTENDED_FAMILY`

| # | answer_key | Question text | Type | Required | Drives |
|---|---|---|---|---|---|
| 35 | `has_maternal_grandparents_info` | Do you have information about your mother's parents? | RADIO (Yes/No) | Yes | `GRANDMOTHER`/`GRANDFATHER`, `maternalPaternalSide: MATERNAL` |
| 36 | `has_paternal_grandparents_info` | Do you have information about your father's parents? | RADIO (Yes/No) | Yes | Same, `PATERNAL` |
| 37 | `has_aunts_uncles` | Do you have any aunts or uncles? | RADIO (Yes/No) | Yes | `AUNT`/`UNCLE` |
| 38 | `aunt_uncle_count` | How many? | NUMBER | No | Reconciliation key already wired. Live-verified. |
| 39 | `has_cousins` | Do you have any cousins you'd like to include? | RADIO (Yes/No) | Yes | `COUSIN` |
| 40 | `cousin_count` | How many? | NUMBER | No | Reconciliation key already wired |
| 41 | `has_nieces_nephews` | Do you have any nieces or nephews? | RADIO (Yes/No) | Yes | `NIECE`/`NEPHEW` |
| 42 | `niece_nephew_count` | How many? | NUMBER | No | Reconciliation key already wired |

Grandparent counts are deliberately not reconciled (`GRANDMOTHER`/`GRANDFATHER` are excluded from `FamilyMemberService.SINGULAR_RELATIONSHIP_TYPES` by design) — consistent with how the backend already treats them.

---

## Section G: Special pedigree scenarios — still not seedable

Twins, adoption, pregnancy loss, assisted reproduction — depend on `TWINSHIP`/`ADOPTION`/`PREGNANCY_OUTCOME`/`ASSISTED_REPRODUCTION`, designed (`QP-Pedigree-Schema-Design.md` §2.6–2.9) but not implemented. Unchanged from v1 — not revisited this pass.

---

## What changed from v1 (mentor review, 2026-10-14)

A mentor review of v1 found it conflated "seeding a question" with "the backend actually executing its intended behavior," and found one real entity-design gap underneath the question wording. Backend changes made this pass, all live-verified before any seeding happened:

1. **Sex/gender migration implemented** (`QP-Pedigree-Schema-Design.md` §3.2, locked since 2026-09-30, implemented now): `Person`/`FamilyMember.genderAtBirth` (old `MALE`/`FEMALE`/`INTERSEX`/`UNKNOWN` enum) replaced by `sexAssignedAtBirth` (`UNKNOWN`/`MALE`/`FEMALE`). `genderIdentity` reshaped in place from free-text `VARCHAR` to a structured enum (`MAN`/`WOMAN`/`NONBINARY`/`UNKNOWN`). New independent `isIntersex` boolean and `countryOfOrigin` (ISO alpha-2) columns. Liquibase changeset `016`.

2. **`PartnerRelationship.matingLineType` conflation fixed.** The mentor review's sharpest finding: `matingLineType` was being asked as one question trying to represent both consanguinity *and* marital/separation status — but a couple can be married **and** biologically related at the same time, and the old enum (`MATING_LINE`/`CONSANGUINEOUS`/`SEPARATED`/`DIVORCED`/`NO_LONGER_TOGETHER`/`CASUAL_OR_UNKNOWN`) had no `MARRIED`/`WIDOWED`/`NEVER_MARRIED` values at all. New independent fields added: `relationshipStatus` (`MARRIED`/`DIVORCED`/`SEPARATED`/`NEVER_MARRIED`/`WIDOWED`/`UNKNOWN`) and `consanguineous` (boolean). `consanguinityDegree`'s required-when condition moved from `matingLineType = CONSANGUINEOUS` to `consanguineous = true`.
   **`matingLineType` itself is untouched** — still its own field, still defaults to `MATING_LINE`, still directly settable via the API for any caller that wants it. It is **not** asked as a question anymore and nothing derives it from the two new fields. `matingLineType` is the literal rendering code from the target pedigree export format (`pedigree-rendering-legend-pswg2022_simplify.json`'s `matingLineCode` — a single visual line-style slot: solid line, double line for consanguineous, slash for separated, etc.), and deciding how to render a couple who are both married and consanguineous is export-time logic. No pedigree export exists in this codebase yet, so that question was explicitly deferred rather than answered — not this pass's job.

3. **A real orphaned-validation bug caught while making this change**: `endedYear`'s "only valid when ended" check was gated solely on `matingLineType`, which the question set no longer submits — so `relationship_ended_year` would always have been rejected. Fixed by gating on `relationshipStatus` (`SEPARATED`/`DIVORCED`) as well, in addition to the existing `matingLineType` check (kept for direct-API callers).

4. **Two answer-writer bugs the review itself missed, caught during the alignment audit**: `vital_status` isn't a recognized answer_key (the writers only match the literal `deceased`) — would have silently landed in `additional_facts`, never touching the real column. Renamed to `deceased` in the question set. `age_at_death` has no dedicated column either (same as `cause_of_death`, which v1 already knew) — documented honestly rather than implying it's a real field.

5. **"Drives" column corrected throughout** for `SIBLINGS`/`PARTNERS`/`CHILDREN`: v1 implied answering a question automatically creates relationship edges. It doesn't — `FamilyMemberController.create` never touches `PARENT_CHILD_RELATIONSHIP`/`PARTNER_RELATIONSHIP`, and nothing in the codebase loops to collect N repeated-question instances automatically. Every edge needs its own explicit API call, stated honestly now rather than implied as automatic.

All 16 new/changed backend tests pass (`RelationshipPersistenceIntegrationTest` grew from 13 to 16), full suite 104/104, 0 failures. The seeded question set was live-verified end-to-end against the real dev server: proband walk through all 6 templates and every question type (TEXT/NUMBER/DATE/RADIO/CHECKBOX), conditional visibility rules, cross-workflow navigation, family-member `DEMOGRAPHICS` reuse, the new `relationshipStatus`/`consanguineous` fields persisting correctly via `POST .../relationships/partners` while `matingLineType` stayed at its untouched default, and the FR-13.2/13.3 reconciliation endpoint correctly flagging a declared-vs-actual mismatch.
