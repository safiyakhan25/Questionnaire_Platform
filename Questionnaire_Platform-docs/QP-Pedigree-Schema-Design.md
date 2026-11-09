# Pedigree Data Model — Schema Design

| | |
|---|---|
| **Document status** | Draft for review — 4 unresolved open questions remain, see §6 |
| **Purpose** | Everything that needs to be added or changed in the data model to produce output matching the PedigreeDraw tool's input format |
| **Revision** | v3 (2026-09-30) — incorporates a field-by-field mentor review of v2 and the `QP-Pedigree-Mapping-Specification.md`. Locks in the gender/sex decision, corrects the `settings`/`_coverageMap` handling, tightens the AMAB/AFAB derivation rule, and adds an implementation sequencing plan (§8). See §9 for exactly what changed from v2. |
| **Depends on** | `pedigree_schema.json` (complete schema), `family_of_10_newschema_V4.json` (populated example), `pedigree-rendering-legend-pswg2022_simplify.json` (vocabulary reference), `QP-Pedigree-Mapping-Specification.md` (field-by-field checklist derived from this doc) |

This document is design only — **nothing in here is implemented yet**. No entity, migration, or mapper has been written from it.

---

## 1. Decisions confirmed so far

| # | Question | Decision | When |
|---|---|---|---|
| 1 | Model family relationships by unifying PERSON/FAMILY_MEMBER, or extending the existing proband-centric model? | **Extend the existing model.** New relationship tables link two individuals (each either the proband or a family member) to each other. | 2026-09-28 |
| 2 | Add a real disease catalog, or keep `disease_type` free text? | **Add a global `DISEASE_CATALOG`.** | 2026-09-28 |
| 3 | Design `geneticTests[]` now that the complete schema gives it a full, unambiguous shape? | **Yes — in scope.** | 2026-09-30 |
| 4 | Design twinship/adoption/pregnancyOutcome/assistedReproduction now that all four have complete formal schemas? | **Yes — in scope**, implemented in questionnaire-flow order once each area's question content is actually defined — not required to land all at once. | 2026-09-30 |
| 5 | What happens when a family has more than 4 distinct diseases (the schema hard-caps `diseases[]` at 4)? | **Open — deliberately left undecided.** To be discussed with your mentor. See §6.1. | 2026-09-30 |
| 6 | Gender/sex mapping | **Decided — see §3.2.** Replace `gender_at_birth` with three separate fields: `sex_assigned_at_birth`, a structured `gender_identity`, and `is_intersex`. | 2026-09-30 |
| 7 | Mentor review of v2 confirmed the relationship-table design (`PARENT_CHILD_RELATIONSHIP`/`PARTNER_RELATIONSHIP`), the `DISEASE`/`DISEASE_CATALOG` split, and the `MUTATION` reshape as correct and matching the complete schema. | **No change needed** — validated as-is. | 2026-09-30 |

---

## 2. New entities

> **Correction, 2026-10-25**: this section's relationship model (§2.2/§2.3 below) is still accurate for `PARENT_CHILD_RELATIONSHIP`/`PARTNER_RELATIONSHIP` as edge-level storage, but it predates a real structural change made 2026-10-23: `FAMILY_MEMBER.relationship_type` no longer exists. It was replaced by a new `family_member_role` table (one row per role, keyed by scope), because recursive adding (any family member can anchor further relatives, not only the proband) means one real person can hold more than one true relational label. See `QP-Family-Relationship-Persistence-Design.md` for the full decision record and §7 item 11 below for what's built vs. still open. Nothing in §2.2/§2.3 needs to change as a result — this note exists so a reader doesn't assume `FAMILY_MEMBER.relationship_type` is still how a role gets recorded.

### 2.1 `DISEASE_CATALOG`

Global, admin-managed, unlimited-size catalog — every disease type your question bank ever asks about gets one entry here, for data-quality/consistency. **Separate** from which ≤4 diseases get featured with a quadrant color in any *one* pedigree export (export-time selection — see §6.1).

| Column | Type | Notes |
|---|---|---|
| `id` | UUID, PK | |
| `name` | VARCHAR, NOT NULL, UNIQUE | e.g. `"Breast cancer"` |
| `abbreviation` | VARCHAR, nullable | e.g. `"BC"` — maps to `diseases[].abbreviation` |
| `default_color` | VARCHAR, nullable | hex color — maps to `diseases[].color` |
| `created_at` / `updated_at` | TIMESTAMP, NOT NULL | |

### 2.2 `PARENT_CHILD_RELATIONSHIP`

| Column | Type | Notes |
|---|---|---|
| `id` | UUID, PK | |
| `proband_id` | UUID, NOT NULL, FK → `PERSON(id)` | scopes the row to one pedigree |
| `parent_person_id` | UUID, nullable, FK → `PERSON(id)` | set when the parent is the proband |
| `parent_family_member_id` | UUID, nullable, FK → `FAMILY_MEMBER(id)` | set when the parent is a family member |
| `child_family_member_id` | UUID, NOT NULL, FK → `FAMILY_MEMBER(id)` | the proband is never a "child" here — their own parent link already exists via `FAMILY_MEMBER.relationship_type` |
| `created_at` | TIMESTAMP, NOT NULL | |

XOR: exactly one of `parent_person_id`/`parent_family_member_id`. Siblings/half-siblings stay derived (shared parent edges), not stored.

### 2.3 `PARTNER_RELATIONSHIP`

| Column | Type | Notes |
|---|---|---|
| `id` | UUID, PK | |
| `proband_id` | UUID, NOT NULL, FK → `PERSON(id)` | |
| `person_a_person_id` / `person_a_family_member_id` | UUID, nullable, FK | XOR |
| `person_b_person_id` / `person_b_family_member_id` | UUID, nullable, FK | XOR |
| `mating_line_type` | ENUM, NOT NULL, default `MATING_LINE` | `MATING_LINE`, `CONSANGUINEOUS`, `SEPARATED`, `DIVORCED`, `NO_LONGER_TOGETHER`, `CASUAL_OR_UNKNOWN` |
| `reproductive_status` | ENUM, NOT NULL, default `UNKNOWN` | `HAS_CHILDREN`, `HAS_CHILDREN_NOT_SHOWN`, `NO_OFFSPRING`, `INFERTILE`, `NO_ISSUE_BY_CHOICE`, `PREGNANCY_ONLY`, `UNKNOWN` |
| `unshown_children_count` | INTEGER, nullable | **required when `reproductive_status = HAS_CHILDREN_NOT_SHOWN`** — enforce at the app layer |
| `consanguinity_degree` | VARCHAR, nullable | **required when `mating_line_type = CONSANGUINEOUS`** |
| `ended_year` | INTEGER, nullable | **only valid when `mating_line_type` is `SEPARATED`, `DIVORCED`, or `NO_LONGER_TOGETHER`** — schema explicitly forbids it otherwise |
| `created_at` | TIMESTAMP, NOT NULL | |

**These three conditional rules are data-collection rules, not just exporter logic** — per the mentor review, the questionnaire's own validation needs to enforce them (e.g. asking for `unshown_children_count` only becomes required once `reproductive_status = HAS_CHILDREN_NOT_SHOWN` is selected), not just check them at export time.

Acknowledged redundancy with `FAMILY_MEMBER.relationship_type = SPOUSE` — intentional, this table holds detail the existing field never could.

### 2.4 `DISEASE` — revised

| Column | Type | Notes |
|---|---|---|
| `id` | UUID, PK | unchanged |
| `person_id` / `family_member_id` | XOR, unchanged | |
| `disease_catalog_id` | UUID, NOT NULL, FK → `DISEASE_CATALOG(id)` | every diagnosis is catalogued, regardless of whether it's "featured" (`diseaseRefId`) in any specific export |
| `code_system` | ENUM, NOT NULL | `ICD_10`, `ICD_O_3`, `OMIM`, `HPO`, `SNOMED_CT`, `CUSTOM` |
| `code_id` | VARCHAR, NOT NULL | e.g. `"C50.9"` |
| `code_label` | VARCHAR, nullable | e.g. `"Breast cancer"` |
| `age_at_diagnosis` | INTEGER, nullable | |
| `laterality` | ENUM, nullable | `LEFT`, `RIGHT`, `BILATERAL` |
| `status` | ENUM, NOT NULL | `CONFIRMED`, `SUSPECTED`, `REPORTED` |

**Ontology coding for patient-entered diagnoses — proposed, not yet confirmed** (see §6.3): patients don't typically know ICD-10/SNOMED codes. Proposal: `DISEASE_CATALOG` gains a *default* code (`default_code_system`/`default_code_id`/`default_code_label`), auto-filled onto a `DISEASE` row when its catalog entry is selected, overridable later by clinical review.

### 2.5 `MUTATION` — revised, not replaced

Functionally the same concept as the complete schema's `geneticTest` (gene + finding), just formally specified. `MUTATION` has **0 rows** today, so reshaping in place is zero-risk.

| Column | Type | Notes |
|---|---|---|
| `id` | UUID, PK | unchanged |
| `person_id` / `family_member_id` | XOR, unchanged | |
| `gene` | VARCHAR, NOT NULL | HGNC symbol, e.g. `"BRCA1"` |
| ~~`mutation`~~ | *removed* | replaced by the three fields below |
| `result` | ENUM, NOT NULL | `PATHOGENIC`, `LIKELY_PATHOGENIC`, `VUS`, `LIKELY_BENIGN`, `BENIGN`, `NEGATIVE`, `PENDING` |
| `variant` | VARCHAR, nullable | HGVS notation |
| `test_type` | ENUM, nullable | `SINGLE_GENE`, `PANEL`, `WES`, `WGS`, `TARGETED` |

The table name `MUTATION` stays (no reason to rename a physical table), but its semantics going forward are really "genetic test result," not just "a mutation" — document that shift wherever this table is referenced going forward.

### 2.6 `TWINSHIP` + `TWINSHIP_MEMBER`

A twin *group* (2 for twins, 3+ for higher-order multiples), not a pairwise link.

**`TWINSHIP`**: `id` (PK), `proband_id` (FK, scope), `twin_type` ENUM (`MONOZYGOTIC`, `DIZYGOTIC`, `ZYGOSITY_UNKNOWN`, `HIGHER_ORDER_MULTIPLE`, `CONJOINED`), `created_at`.

**`TWINSHIP_MEMBER`**: `id` (PK), `twinship_id` (FK), `person_id`/`family_member_id` (XOR — a twin could be the proband). All members sharing one `twinship_id` are each other's `coTwins`.

### 2.7 `ADOPTION` + `ADOPTION_PARENT`

**`ADOPTION`**: `id` (PK), `proband_id` (FK, scope), `individual_person_id`/`individual_family_member_id` (XOR), `adoption_type` ENUM (`ADOPTED_IN`, `ADOPTED_OUT`), `created_at`.

**`ADOPTION_PARENT`**: `id` (PK), `adoption_id` (FK), `parent_role` ENUM (`ADOPTIVE`, `BIOLOGICAL`), `parent_person_id`/`parent_family_member_id` (XOR). `adoptiveParents` required when `adoption_type = ADOPTED_IN` — enforce at the app layer.

### 2.8 `PREGNANCY_OUTCOME`

A pregnancy loss or current pregnancy gets its own `FAMILY_MEMBER` row (never the proband), with this table attached.

| Column | Type | Notes |
|---|---|---|
| `id` | UUID, PK | |
| `family_member_id` | UUID, NOT NULL, UNIQUE, FK → `FAMILY_MEMBER(id)` | |
| `outcome_type` | ENUM, NOT NULL | `CURRENT_PREGNANCY`, `AFFECTED_PREGNANCY`, `PREGNANCY_LMP`, `SAB`, `SAB_AFFECTED`, `TOP`, `TOP_AFFECTED`, `ECTOPIC`, `STILLBIRTH` |
| `gestational_weeks` | INTEGER, nullable | 0–45 |
| `lmp_date` | DATE, nullable | |
| `created_at` | TIMESTAMP, NOT NULL | |

### 2.9 `ASSISTED_REPRODUCTION` + `ASSISTED_REPRODUCTION_PARTICIPANT`

**`ASSISTED_REPRODUCTION`**: `id` (PK), `proband_id` (FK, scope), `individual_person_id`/`individual_family_member_id` (XOR), `reproduction_type` ENUM (`DONOR`, `SPERM_DONOR`, `EGG_DONOR`, `GESTATIONAL_CARRIER`, `SURROGATE`, `INTENDED_PARENT`, `IVF`, `IUI`, `ICSI`, `EMBRYO_ADOPTION`, `ANONYMOUS_DONOR`), `anonymous` BOOLEAN nullable, `created_at`.

**`ASSISTED_REPRODUCTION_PARTICIPANT`**: `id` (PK), `assisted_reproduction_id` (FK), `participant_role` ENUM (`EGG_DONOR`, `SPERM_DONOR`, `GESTATIONAL_CARRIER`, `INTENDED_PARENT`, `DONATED_TO`, `CARRIED_FOR`), `participant_person_id`/`participant_family_member_id` (XOR).

### 2.10 `GENDER_TRANSITION`

| Column | Type | Notes |
|---|---|---|
| `id` | UUID, PK | |
| `person_id` / `family_member_id` | XOR, UNIQUE per individual | |
| `from_gender` | ENUM, NOT NULL | `MAN`, `WOMAN`, `NONBINARY`, `UNKNOWN` — the new `gender_identity` enum from §3.2, now decided |
| `to_gender` | ENUM, NOT NULL | same |
| `transition_year` | INTEGER, nullable | |
| `created_at` | TIMESTAMP, NOT NULL | |

### 2.11 `CANCER_RISK_PROFILE` — proposed, scope not yet decided

**Missed by both v1 and v2 — found while writing `QP-Pedigree-Mapping-Specification.md`, and the mentor review flagged it needs an explicit scope decision before implementation, not a silent addition.** Maps to `individual.extensions.cancerRiskModel` — feeds CanRisk/BOADICEA cancer-risk export tools.

| Column | Type | Notes |
|---|---|---|
| `id` | UUID, PK | |
| `person_id` / `family_member_id` | XOR | |
| `er`, `pr`, `her2`, `ck14`, `ck56` | ENUM, nullable each | `POSITIVE`, `NEGATIVE` — breast tumor pathology markers |
| `mlh1`, `msh2`, `msh6`, `pms2` | ENUM, nullable each | `INTACT`, `LOST`, `EQUIVOCAL` — MMR immunohistochemistry (Lynch syndrome) |
| `created_at` | TIMESTAMP, NOT NULL | |

**Do not implement this table until §6.4 is resolved**: is cancer-risk-model data part of QP's required collection scope, or only needed later for a CanRisk/BOADICEA integration that's a separate concern from the questionnaire itself?

---

## 3. Changes to existing entities

### 3.1 `PERSON` / `FAMILY_MEMBER` — ancestry and examination status

| Column | Type | Maps to | Status |
|---|---|---|---|
| `country_of_origin` | array of CHAR(2), nullable | `ancestry.countryOfOrigin` — ISO 3166-1 alpha-2 codes (`^[A-Z]{2}$`), not free text | Confirmed |
| `not_examined` | BOOLEAN, nullable | `notExamined` | **Downgraded — see below** |
| `external` | BOOLEAN, nullable | `external` | **Downgraded — see below** |

**Revised per mentor review**: v2 proposed `not_examined` and `external` as confident low-risk additions, reasoning purely from "the target schema supports it." That's backwards — both are *optional* in the target schema, so schema support alone isn't justification for adding them. The right question is whether QP's own business/questionnaire actually needs to distinguish these concepts:
- **`external`**: no current business need identified. **Default: don't add it** unless a real requirement for "individual outside the direct pedigree, no further history" surfaces.
- **`not_examined`**: only worth adding if the questionnaire/business workflow actually needs to distinguish "known unaffected" from "not personally examined, info from family report only." Not yet justified. **Default: don't add it**, revisit if the need becomes concrete.

Both moved from "confident addition" to an open, default-to-no scope question — see §6.4.

`ancestry.selfReportedEthnicity` still needs no change (`ethnicity[]` already exists). `ancestry.inferredAncestry` requires genetic ancestry inference we don't perform — permanently null for us, not something to build toward.

### 3.2 Sex, gender, and intersex — decided

**Replaces the earlier open question.** `GenderAtBirthType` (`MALE`/`FEMALE`/`INTERSEX`/`UNKNOWN`) is removed. Three separate fields replace it, each matching one of the target schema's actual, separate concepts:

| New/changed field | Type | Maps to | Notes |
|---|---|---|---|
| `sex_assigned_at_birth` | ENUM, `UNKNOWN`/`MALE`/`FEMALE` | `sex` (`0`/`1`/`2`) | Replaces `gender_at_birth`. Direct 1:1 mapping — `UNKNOWN→0, MALE→1, FEMALE→2`. |
| `gender_identity` | **changed from free-text VARCHAR to ENUM**, `MAN`/`WOMAN`/`NONBINARY`/`UNKNOWN` | `gender` (`1`/`2`/`3`/`4`) | Same column name, different type — the existing free-text column is converted, not kept alongside a new one. `MAN→1, WOMAN→2, NONBINARY→3, UNKNOWN→4`. |
| `is_intersex` | BOOLEAN | `individualAnnotation: intersexAnnotation` | New. Independent of both enums above — intersex is annotation-only in the target schema, never a value of `sex` or `gender`. |

**AMAB/AFAB annotation — precise deterministic rule** (tightened per mentor review; "computed when the two diverge" was too broad and risked misclassifying nonbinary/unknown combinations):

```
sex_assigned_at_birth = MALE   AND gender_identity = WOMAN  → amabAnnotation
sex_assigned_at_birth = FEMALE AND gender_identity = MAN    → afabAnnotation
anything else (including any combination involving UNKNOWN or NONBINARY) → no AMAB/AFAB annotation
```

Computed at export time — never stored.

**Practical implication**: this converts one intake question (gender at birth) into at least two (sex assigned at birth, gender identity), possibly three including intersex status. This is a real, visible change to the Personal Details question set, not just a backend schema change — worth remembering when task 1 (more question templates) comes around.

---

## 4. Out of scope, or handled differently than "always null"

- **`settings`** (fonts, colors, layout, pagination, PNG/PDF/SVG output, determinism) and **`_coverageMap`** — **not nullable in the schema** (`"type": "object"`, no `null` in the type union). Sending `{"settings": null}` would fail schema validation. These must be **omitted from the output entirely**, not set to `null`. See the corrected status vocabulary in `QP-Pedigree-Mapping-Specification.md` (`Omitted`, a new category, distinct from `Always null`).
- **`groupSymbolCode`/`groupCount`** ("5 unaffected sisters" as one collapsed symbol) — doesn't fit how our questionnaire collects data (one family member at a time). Always omitted, never populated.

---

## 5. Computed at export time — not stored anywhere

| Field | How it's computed |
|---|---|
| `role`, `affectedAndCarrier` (unaffected/affected/affectedMultiple only), `siblings[]`/`halfsiblings{}`, `generation`, `individualLabel` | Unchanged from earlier revisions. |
| `individualLabel` for donors/gestational carriers | `ED-1`/`GC-1` label pattern, separate branch from the generation-based `II-3` form. |
| `diseases[].quadrant` | **Only 4 of the 5 possible values are currently assignable by our export logic**: `upperLeft`/`upperRight`/`lowerRight`/`lowerLeft`, clockwise, capped at 4 catalog entries per export. The schema's 5th value, `centerDot`, exists but **we don't assign it automatically** — no rule has been designed for when a disease should render as a center dot instead of a quadrant. Not a blocker (it's an additional rendering option, not a required one), just noted so it isn't mistaken for an oversight. |
| `individualAnnotation: amabAnnotation`/`afabAnnotation` | Per the precise rule in §3.2. |

---

## 6. Open questions — do not implement until resolved

**Revised list following mentor review — down from the earlier framing to exactly 4 items** (the gender/sex question is now decided, see §3.2, and is no longer open):

### 6.1 The >4-diseases rule

**Deliberately left open, to discuss with your mentor.** The schema hard-caps `diseases[]` at 4 entries per export. `DISEASE_CATALOG` (unlimited, for data quality) is separate from the ≤4 "featured" diseases any *one* export selects — nothing about the catalog depends on this answer. But the export-time selection logic (pick top 4 by what rule? reject the export? ask a human?) can't be built until this is decided.

### 6.2 Carrier-status derivation

**New, raised by mentor review.** `affectedAndCarrier` includes `carrierHet`, `asymptomaticCarrier`, `obligateCarrier` — these need a derivation rule from `MUTATION`/genetic-test data (e.g. "heterozygous pathogenic finding → `carrierHet`"), and no such rule has been designed. **Explicitly not Phase 1** — the genetic-test data model needs to exist and be populated first, then a separate, deliberate derivation rule needs designing. Don't infer one ad hoc during implementation.

### 6.3 Ontology coding for patient-entered diagnoses

Raised in §2.4: patients don't typically know ICD-10/SNOMED codes. Proposed default (catalog carries a default code, auto-filled, overridable) is *not yet confirmed* — flagging it here rather than treating the proposal as settled.

### 6.4 `external`/`notExamined` — and `CANCER_RISK_PROFILE` scope

Per §3.1: default is **don't add** `external`/`not_examined` unless a concrete business need shows up — this is a leaning, not a hard block, so it's listed here mainly so the default isn't silently forgotten. Per §2.11: `CANCER_RISK_PROFILE` needs an explicit answer from your mentor — is `cancerRiskModel` data part of QP's required collection scope, or only relevant to a later CanRisk/BOADICEA integration that's a separate concern? If required → Phase 1 data model. If not → Phase 2, and it shouldn't block core questionnaire work.

---

## 7. Implementation sequencing — Phase 1A / Phase 1B

**New — added per mentor review.** The recommendation is explicit: **don't define "Phase 1" as "implement every pedigree field."** Stabilize what's already built first, then make the data model pedigree-ready, in this order:

### Phase 1A — stabilize questionnaire execution (no pedigree-specific work yet)

1. Fix the remaining malformed-rule/seed-data issues in the JSONLogic configuration
2. ✅ **Done** — Family-member `POST` API (add a relative mid-session). `FamilyMemberController.create()`/`FamilyMemberService.create()`, confirmed present 2026-10-25, extended 2026-10-23 for recursive scope.
3. ✅ **Done** — Family-member `DELETE` API. `FamilyMemberController.delete()`/`FamilyMemberService.delete()`, with reference-integrity guards (active pass, recorded answers, relationship references, scope-for-another-role), confirmed present 2026-10-25.
4. ✅ **Done** — Family-member answer execution. `FamilyMemberAnswerWriter` (confirmed present 2026-10-25) mirrors `PersonAnswerWriter`'s answer-key → column mapping for `FAMILY_MEMBER`.
5. ✅ **Done** — Relationship-instance handling. `relationship_instance_id` is read/written on `QUESTION_RESPONSE` (confirmed present in `AssessmentService`/`QuestionResponseRepository`/`QuestionResponse` 2026-10-25) — no longer unused.
6. ✅ **Done** — Runtime answer validation (FR-15). `AnswerValidator` (confirmed present 2026-10-25) validates required/type/range/pattern/allowed-values against `question.validation`/`question.options` before persisting, shared with `AssessmentService`'s FR-6 completion check via `isAnswered()`. A companion `ConfigurationValidator` (also confirmed present) validates the same `validation`/`options` shape at admin save time, not found as a separate planned item in earlier revisions of this document but worth recording here since it closes the same FR-15 gap from the other direction (reject bad config at write time, not just bad answers at read time).
7. ✅ **Done, and independently verified 2026-10-25** (corrects the same-day earlier note in this document, which had flagged this as unconfirmed before the tests were actually read). `AssessmentFlowIntegrationTest.sessionCompletesAndResumeRulesAreEnforced` directly covers the completed-session gap: once a session is `COMPLETED`, `/start` on it throws `ResourceNotFoundException` rather than silently resuming, and submitting a further answer throws rather than silently succeeding. FR-6 required-question gating is covered at three independent layers: `AnswerValidatorTest.requiredQuestionRejectsBlankValue` (unit), `AssessmentFlowIntegrationTest.requiredQuestionBlocksTemplateCompletion` (service — asserts `TemplateIncompleteException` names the specific missing `answer_key`), and `InvalidRequestsIntegrationTest.blankRequiredAnswerReturns400` (HTTP — asserts the exact `ApiError.message` text). Resume itself (not just completion) is separately covered by `AnswerEditingIntegrationTest.resumeAtTemplateMovesTheRealPositionEnablingAnEdit`, `.resumeAtTemplateUncompletesASession`, and `.editingAnEarlierAnswerCascadesInvalidationToDownstreamData`, plus `AssessmentFlowIntegrationTest.resumingAnInProgressSessionDoesNotCreateADuplicatePerson`. Full suite run 2026-10-25: 118/118 passing.
8. **Partially done** — disease/mutation answer persistence. `DiseaseAnswerWriter`/`MutationAnswerWriter` (confirmed present 2026-10-25) already reconcile the `"diseases"`/`"genetic_findings"` answer keys into `DISEASE`/`MUTATION` rows on every submit (full delete-and-reinsert, so edits and clears both work). What's still outstanding is the **table reshape itself** (`DISEASE_CATALOG`, ontology `code`, `laterality`, `status` on `DISEASE`; `result`/`variant`/`test_type` on `MUTATION` replacing the free-text `mutation` column) — see §2.4/§2.5 below, unchanged from v3. The persistence *plumbing* this item originally worried about not existing is done; the *shape* it needs to write is not.
9. ✅ **Done (2026-10-14)** — Apply the §3.2 sex/gender/intersex model change. Liquibase `016`: `gender_at_birth` → `sex_assigned_at_birth` (`UNKNOWN`/`MALE`/`FEMALE`), `gender_identity` reshaped from free text to the `MAN`/`WOMAN`/`NONBINARY`/`UNKNOWN` enum, new independent `is_intersex` boolean.
10. ✅ **Done (2026-10-14)** — no legacy rules referenced the old `gender_at_birth` answer key in the real dev DB (confirmed before migrating), so nothing needed updating.
11. ✅ **Done (2026-10-23), found during the 2026-10-25 re-verification, not in any earlier revision of this document** — `FAMILY_MEMBER.relationship_type` was removed entirely and replaced by a new `family_member_role` table (Liquibase `017`), enabling recursive adding (any family member can be the scope/anchor for further relatives, not only the proband) and letting one real person hold more than one true relational label. See `QP-Family-Relationship-Persistence-Design.md` for the full record — this is a structural change to §2.2/§11 of this document's own model, not an addition alongside it. `FamilyMemberService` also now **auto-creates** `PARENT_CHILD_RELATIONSHIP`/`PARTNER_RELATIONSHIP` edges for the common role types (parent/child/spouse) at creation time, including a second-parent auto-attach when the scope has exactly one `HAS_CHILDREN` partner on record (not retroactive yet — see `QP-Pedigree-Mapping-Specification.md` §17 for what's still unbuilt around this).
12. ✅ **Done, found during the 2026-10-25 re-verification** — `PedigreeExportService`/`PedigreeExportController` (Phase 2 in §7 below) already exist and compute the relationship graph (`parents[]`, `siblings[]`, `halfsiblings{}`, `children{}`, `spouses{}`, `generation`, `individualLabel`) end to end, including a provisional `matingLineCode` derivation rule. This is ahead of where this document's own Phase 1/Phase 2 split assumed things stood — see `QP-Pedigree-Export.md` for the full account and `QP-Pedigree-Mapping-Specification.md` v3 for the field-by-field status this enables.

This preserves the architecture's existing separation of questionnaire configuration, execution, and business state — none of this is pedigree-specific, it's finishing what's already started.

### Phase 1B — make the QP data model pedigree-ready

11. `DISEASE_CATALOG`
12. Modify `DISEASE`
13. Modify `MUTATION`
14. `GENDER_TRANSITION`
15. ✅ **Done (2026-10-12)** — `PARENT_CHILD_RELATIONSHIP`
16. ✅ **Done (2026-10-12; extended 2026-10-14)** — `PARTNER_RELATIONSHIP`. The 2026-10-14 extension added `relationship_status` and `consanguineous` as independent fields, decoupled from `mating_line_type` — see `QP-Pedigree-Demographic-Questions.md`'s "What changed from v1" section for the full reasoning. `mating_line_type` itself is unchanged, still the literal pedigree-rendering code (`matingLineCode`), not derived from the new fields — that derivation is export-time logic, not yet built.
17. ✅ **Done (2026-10-14)** — `country_of_origin`
18. ✅ **Done (2026-10-14)** — `is_intersex`
19. Resolve §6.4 (`not_examined`/`external`/`CANCER_RISK_PROFILE` scope) and act on the answer
20. Then, once their own question content exists: `TWINSHIP`, `ADOPTION`, `PREGNANCY_OUTCOME`, `ASSISTED_REPRODUCTION` — later in the sequence because they're specialized structures, not because they're deprioritized in principle (per decision #4 in §1, they're in scope)

### Phase 1 completion criteria

Phase 1 is done when this works, **without needing any Pedigree API connectivity yet**:

```
start session → PERSON created → details collected → sex/gender/intersex stored
→ rules execute correctly → family member created → family member details collected
→ relationships persisted → disease persisted → genetic test persisted
→ responses persisted → session completed → full QP domain state retrievable
```

At that point you can say "the QP database now contains everything the agreed pedigree mapping requires" — and Phase 2 (QP DB → Pedigree Mapper → Pedigree JSON → Schema Validator → Pedigree API) becomes a much more contained, mechanical piece of work on top of a settled data model, instead of something built and re-built alongside a moving schema.

---

## 8. Revision history

**v4 (2026-10-25)**: re-verified this entire document directly against the running code (not just against the mapping spec) for the first time since v3. Found and corrected: `family_member_role` replacing `FAMILY_MEMBER.relationship_type` (2026-10-23, not reflected anywhere in v3 — §2, §7 item 11); `PedigreeExportService`/`PedigreeExportController` already built and computing the full relationship graph, ahead of the Phase 1/Phase 2 split this document assumed (§7 item 12); family-member POST/DELETE/answer-execution, relationship-instance handling, and runtime answer validation (FR-15) all done (§7 items 2-6), none of which were marked done in v3; disease/mutation answer *persistence* done even though the table *reshape* (§2.4/§2.5) is not (§7 item 8). No open decision from §6 was resolved by this pass — all four remain open exactly as in v3. See `QP-Pedigree-Mapping-Specification.md` v3 (2026-10-25) for the companion field-by-field update.

**v3 (2026-09-30)**: incorporated mentor review of v2. Locked in the gender/sex decision (§3.2). Corrected `settings`/`_coverageMap` from implicit "always null" to explicit "omitted, not nullable" (§4). Tightened AMAB/AFAB to a precise rule instead of "whenever they diverge" (§3.2). Downgraded `external`/`not_examined` from confident additions to open, default-to-no questions (§3.1, §6.4). Added `CANCER_RISK_PROFILE` with explicit pending-scope framing (§2.11). Added the centerDot/quadrant clarification (§5). Added the Phase 1A/1B implementation sequencing plan (§7), which didn't exist in v2.

**v2 (2026-09-30, earlier same day)**: rewritten against the complete `pedigree_schema.json`. Corrected v1's placement of ontology coding (moved from `DISEASE_CATALOG` to `DISEASE`) and `countryOfOrigin` (corrected from free text to ISO codes). Brought genetic tests and twinship/adoption/pregnancy/assisted-reproduction into scope, previously deferred.

**v1 (2026-09-28)**: initial draft, written from two sample/reference files before the complete formal schema was available.
