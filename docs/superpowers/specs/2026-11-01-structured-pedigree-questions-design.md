# Structured pedigree questions — design

## Status
Draft, for approval. Written after evaluating a pasted third-party proposal (summarized in
§2) and cross-checking it against the actual pedigree schema docs (`QP-Pedigree-Schema-Design.md`,
`QP-Pedigree-Mapping-Specification.md`) and every existing answer-writer's real code.

## 1. Intent and why

Seven admin-seeded questions can't be answered through the UI today:

| Workflow | Template | answer_key |
|---|---|---|
| PERSONAL_DETAILS | GENDER_TRANSITION | `gender_transition` |
| MEDICAL_HISTORY | CONDITIONS | `diseases` |
| MEDICAL_HISTORY | GENETIC_TESTING | `genetic_findings` |
| FAMILY_FORMATION | TWINSHIP | `twinship` |
| FAMILY_FORMATION | ADOPTION | `adoption` |
| FAMILY_FORMATION | ASSISTED_REPRODUCTION | `assisted_reproduction` |
| FAMILY_FORMATION | PREGNANCY_OUTCOME | `pregnancy_outcome` |

Each is seeded today as `RADIO`/`CHECKBOX` with `options: null`. `QuestionField.jsx` renders every
RADIO/CHECKBOX/DROPDOWN branch as `(options ?? []).map(...)`, so with `options: null` these render
zero inputs — just a bare `<legend>`, nothing to fill in. But the real problem is structural, not
just missing options: the backend answer-writers for all seven already exist
(`DiseaseAnswerWriter`, `MutationAnswerWriter`, `GenderTransitionAnswerWriter`,
`TwinshipAnswerWriter`, `AdoptionAnswerWriter`, `PregnancyOutcomeAnswerWriter`,
`AssistedReproductionAnswerWriter`) and each expects a structured JSON object or array — several
containing references to *other family members already added in this session* — which no existing
`QuestionType` (`TEXT`/`NUMBER`/`DATE`/`RADIO`/`CHECKBOX`/`DROPDOWN`) can produce.

This document designs the fix: one new generic `STRUCTURED` question type, and the exact
`field_config` for each of the seven questions above, extracted directly from each writer's own
parsing code (not guessed from the schema docs alone — the docs describe the *target* shape, the
writers are the actual, already-shipped contract).

## 2. Where the pasted third-party proposal was right, and where it was wrong

The proposal (single generic `STRUCTURED` type + a JSONB field-definition column + a generic
renderer, reusing the already-existing writers) is **directionally correct** — it's the right
architecture, and far better than seven bespoke question types. But it has concrete problems,
checked against the real code:

1. **Wrong field list.** Its example table swaps `genetic_findings` (real, backed by `MUTATION`,
   `MutationAnswerWriter` exists) for a `"Cancer risk profile"` row. No `CANCER_RISK_PROFILE` table
   exists — `QP-Pedigree-Schema-Design.md` §2.11/§6.4 explicitly says **do not implement until
   scope is decided by your mentor**. This document is not in scope for it.
2. **No way to express a repeatable list.** `diseases` and `genetic_findings` are each a JSON
   *array* of entries (a subject can report more than one disease/finding) — not a single flat
   object. The proposal's field-config example has no repeat/list concept at all.
3. **Overstates the disease-catalog complexity.** The proposal implies a catalog-picker UI capped
   at 4 entries. Reading `DiseaseAnswerWriter` shows this isn't needed: a diagnosis is reported as
   free text (`diseaseType`), and the writer does find-or-create against `DISEASE_CATALOG` itself.
   The ≤4-entries cap is a `PedigreeExportService` *export-time* rendering rule (which catalog
   entries get a quadrant color), already implemented, and has nothing to do with how the question
   is answered.
4. **Understates `assisted_reproduction`.** It's not "participant selectors" — it's one object
   with a nested *repeatable list* of `{role, familyMemberId}` pairs (`AssistedReproductionAnswerWriter`
   flattens the schema's six separate participant fields into one role-tagged list).
5. **Overstates the backend work.** All seven writers already parse arbitrary structured JSON and
   already throw `InvalidAnswerException` on a bad shape. The real backend work is small: one new
   `QuestionType` constant, one new JSONB column, and plumbing it through the existing
   `QuestionController`/`AssessmentService`/`QuestionAnswerView` path — not new writers.

## 3. Goals, non-goals, anti-goals

**Goals**
- Every one of the seven questions above becomes answerable through the real UI, producing exactly
  the JSON shape its writer already expects (verified field-by-field below, not inferred).
- One reusable mechanism (`STRUCTURED` type + `field_config`), not seven bespoke ones — so a future
  structured question (if one is ever needed) reuses the same renderer.
- Fix a related regression found during this pass (§7): `country_of_origin` became an array column
  in migration 028, but the seeded question is still `TEXT`, so submitting it today throws
  (`PersonAnswerWriter.stringArray()` requires a JSON array; a TEXT input submits a plain string).

**Non-goals (explicitly out of scope, confirmed against the design docs)**
- `CANCER_RISK_PROFILE` / `extensions.cancerRiskModel` — scope not decided (§6.4 of the schema
  design doc).
- The >4-diseases catalog-selection rule, `obligateCarrier`/`asymptomaticCarrier` derivation,
  `external`/`notExamined` — all separately documented open decisions, untouched here.
- The §17 API gaps (`candidates` endpoint, link-existing-person-into-new-role, retroactive
  second-parent backfill) — unrelated to answering these seven questions.
- A disease-catalog *picker* UI (select from existing catalog entries) — not how
  `DiseaseAnswerWriter` works today; out of scope unless that writer changes first.
- Session/ownership scoping on family-member references inside an answer (e.g. a `twinship`
  answer can currently reference a family member UUID from a different session — a pre-existing
  gap called out in `TwinshipAnswerWriter`'s own comment). Not introduced or worsened by this
  design; not fixed by it either.

**Anti-goals**
- Don't build a general-purpose form-builder. `field_config` only needs to express what these seven
  questions actually need: flat fields, one level of repeatable sub-rows, and picking an existing
  family member. Nothing more speculative than that.

## 4. The `field_config` design

### 4.1 Backend

- New `QuestionType.STRUCTURED` enum constant.
- New column `question.field_config` (JSONB, nullable — same pattern as `options`/`validation`).
  Migration: `ALTER TABLE question ADD COLUMN field_config JSONB`.
- `Question` entity, `QuestionDto`, `QuestionCreateRequest`, `QuestionUpdateRequest`,
  `QuestionAnswerView` each gain a `fieldConfig` (`JsonNode`) field, following exactly the existing
  `options`/`JsonColumnMapper` pattern already used for those DTOs — no new conventions.
- `AssessmentService.toView()` gains one more `jsonColumnMapper.read(question.getFieldConfig())`
  call alongside the existing `options`/`validation` reads.
- `AnswerValidator.validate()`'s `switch (question.getType())` is a switch **statement** with no
  `default` — adding `STRUCTURED` without a case is legal Java and simply skips the
  type-specific branch (the existing `required` check above the switch still applies). Add an
  explicit `case STRUCTURED -> {}` with a one-line comment that deep-shape validation is the
  writer's job (matches how it already behaves for every other type's edge cases) — don't silently
  rely on the no-case-matches fallthrough.
- `ConfigurationValidator.validateForType()` is already a named extension point for exactly this.
  Add a `validateFieldConfig(JsonNode fieldConfig)` check for `STRUCTURED`: `shape` is `"object"` or
  `"list"`, `fields[]` is non-empty, each field has a non-blank `name` + known `type`
  (`TEXT`/`NUMBER`/`DATE`/`DROPDOWN`/`FAMILY_MEMBER_SELECT`/`FAMILY_MEMBER_MULTI_SELECT`/`GROUP_LIST`),
  a `GROUP_LIST` field has its own non-empty nested `fields[]`, and a `DROPDOWN` field has non-empty
  `options`. This catches a malformed admin edit at save time instead of at first patient use.

No changes needed to any of the seven answer-writers — they're the fixed contract this is built
against, not something this feature modifies.

### 4.2 `field_config` shape

```
{
  "shape": "object" | "list",
  "fields": [ FieldDef, ... ]
}
```

- `shape: "object"` — the submitted answer is a single object matching `fields[]` (or `null`,
  everywhere the schema allows "not applicable").
- `shape: "list"` — the submitted answer is an array, each entry matching `fields[]` (or `[]`,
  empty list = "none reported").

`FieldDef`:
```
{
  "name": "...",            // JSON key in the submitted entry/object
  "label": "...",           // shown to the patient
  "type": "TEXT" | "NUMBER" | "DATE" | "DROPDOWN" | "FAMILY_MEMBER_SELECT"
         | "FAMILY_MEMBER_MULTI_SELECT" | "GROUP_LIST",
  "required": true | false,
  "requiredWhen": { "field": "...", "equals": <value> },   // optional, client-side UX only
  "options": [ { "label": "...", "value": ... }, ... ],    // DROPDOWN only
  "fields": [ FieldDef, ... ]                              // GROUP_LIST only — one row's shape
}
```

`TEXT`/`NUMBER`/`DATE`/`DROPDOWN` reuse exactly the same rendering/value semantics
`QuestionField.jsx` already has for top-level questions of those types — the generic renderer
delegates to the same sub-components rather than duplicating them.

`FAMILY_MEMBER_SELECT`/`FAMILY_MEMBER_MULTI_SELECT` are new, frontend-only concepts (no
corresponding top-level `QuestionType` — they only ever appear inside a `STRUCTURED` field_config).
They render as a dropdown/checkbox-list populated from the *current session's* family members
(`client.getFamilyMembers(sessionId)` — the function already exists and is already used by the
sibling `FamilyMemberHub` component, but `sessionId` does not currently reach `QuestionField.jsx`:
today it receives only `{ question, onChange }` from `TemplateView.jsx`. This design requires
threading two more props down the existing `TemplateView` → `QuestionField` chain: `sessionId`
(for the fetch) and the subject's own id — `familyMemberId`, or `null` for the proband — so the
STRUCTURED renderer can exclude the subject itself from its own picker, per §8. Real, if small,
plumbing work — not already wired.

`GROUP_LIST` renders its own `fields[]` as one repeatable row (add/remove row controls), submitting
an array of objects — this is how `assisted_reproduction.participants[]` nests inside an
otherwise flat `shape: "object"` question.

Every `shape: "object"` question that is itself optional per the schema (all five object-shaped
ones below are) gets a leading "Does this apply to this person? Yes / No" toggle in the renderer,
not expressed in `field_config` — when "No" is chosen the submitted value is `null` and the rest of
the fields are hidden, matching exactly what each writer treats as "not applicable" (it already
handles `value == null` by deleting any existing rows and stopping).

**Builder-check finding, resolved here, my call**: none of these seven top-level questions may be
configured with `question.validation.required: true`. `AnswerValidator.isBlank()` treats `null` and
`[]` as blank regardless of *why* they're blank, and `validateTemplateCompletion()`'s required-check
is exactly `!isBlank()` — so a patient who explicitly answers "No, not a twin" (submits `null`) is
indistinguishable from one who never touched the question, and a `required: true` question of this
shape could never be satisfied, permanently blocking template completion. (`DiseaseAnswerWriter`'s
own comment already documents the same null/`[]`-both-mean-nothing-reported convention for `list`
shapes — this is that same convention, just newly load-bearing against the *required* check too.)
Resolution: seed all seven with `validation: null` (optional) — matching what "not applicable" and
"patient hasn't gotten to it yet" both already mean everywhere else in this system. This is
independent of each `FieldDef.required` inside `field_config` (§4.2), which only governs what's
required *within* the object once the patient has chosen "Yes" — that part is unaffected and
already correctly scoped per-field in §5. If a future business need requires distinguishing
"explicitly declined" from "not yet answered," that needs a change to `isBlank`/`isAnswered`
semantics themselves — out of scope for this feature.

## 5. `field_config` for each of the seven questions

Extracted directly from each writer's `parseEntry(s)` method — this is the literal contract, not a
re-derivation from the schema docs.

### `diseases` (shape: `list`, matches `DiseaseAnswerWriter.DiseaseEntry`)
```json
{ "shape": "list", "fields": [
  { "name": "diseaseType", "label": "Disease name", "type": "TEXT", "required": true },
  { "name": "ageAtDiagnosis", "label": "Age at diagnosis", "type": "NUMBER", "required": false }
]}
```

### `genetic_findings` (shape: `list`, matches `MutationAnswerWriter.MutationEntry`)
```json
{ "shape": "list", "fields": [
  { "name": "gene", "label": "Gene", "type": "TEXT", "required": true },
  { "name": "mutation", "label": "Finding (free text)", "type": "TEXT", "required": false }
]}
```

### `gender_transition` (shape: `object`, matches `GenderTransitionAnswerWriter.Entry`)
```json
{ "shape": "object", "fields": [
  { "name": "fromGender", "label": "Gender identity before transition", "type": "DROPDOWN", "required": true,
    "options": [{"label":"Man","value":"MAN"},{"label":"Woman","value":"WOMAN"},
                {"label":"Nonbinary","value":"NONBINARY"},{"label":"Unknown","value":"UNKNOWN"}] },
  { "name": "toGender", "label": "Current gender identity", "type": "DROPDOWN", "required": true,
    "options": [{"label":"Man","value":"MAN"},{"label":"Woman","value":"WOMAN"},
                {"label":"Nonbinary","value":"NONBINARY"},{"label":"Unknown","value":"UNKNOWN"}] },
  { "name": "transitionYear", "label": "Year of transition (if known)", "type": "NUMBER", "required": false }
]}
```

### `twinship` (shape: `object`, matches `TwinshipAnswerWriter.Entry`)
```json
{ "shape": "object", "fields": [
  { "name": "twinType", "label": "Type of multiple birth", "type": "DROPDOWN", "required": true,
    "options": [{"label":"Identical (monozygotic)","value":"MONOZYGOTIC"},
                {"label":"Fraternal (dizygotic)","value":"DIZYGOTIC"},
                {"label":"Zygosity unknown","value":"ZYGOSITY_UNKNOWN"},
                {"label":"Higher-order multiple","value":"HIGHER_ORDER_MULTIPLE"},
                {"label":"Conjoined","value":"CONJOINED"}] },
  { "name": "coTwinFamilyMemberIds", "label": "Co-twin(s) already added as family members",
    "type": "FAMILY_MEMBER_MULTI_SELECT", "required": true }
]}
```

### `adoption` (shape: `object`, matches `AdoptionAnswerWriter.Entry`)
```json
{ "shape": "object", "fields": [
  { "name": "adoptionType", "label": "Adoption type", "type": "DROPDOWN", "required": true,
    "options": [{"label":"Adopted into this family","value":"ADOPTED_IN"},
                {"label":"Adopted out of this family","value":"ADOPTED_OUT"}] },
  { "name": "adoptiveParentFamilyMemberIds", "label": "Adoptive parent(s) already added as family members",
    "type": "FAMILY_MEMBER_MULTI_SELECT", "required": false,
    "requiredWhen": { "field": "adoptionType", "equals": "ADOPTED_IN" } }
]}
```
(The writer itself also enforces this requiredness server-side — the `requiredWhen` is UX, not the
only guard.)

### `pregnancy_outcome` (shape: `object`, matches `PregnancyOutcomeAnswerWriter.Entry`; confirmed
via a live query against the dev DB — `FAMILY_FORMATION > PREGNANCY_OUTCOME` is already seeded
`subject_scope = FAMILY_MEMBER_ONLY` — consistent with the writer throwing on `applyToPerson`, not
just asserted from the writer's shape alone)
```json
{ "shape": "object", "fields": [
  { "name": "outcomeType", "label": "Outcome", "type": "DROPDOWN", "required": true,
    "options": [{"label":"Current pregnancy","value":"CURRENT_PREGNANCY"},
                {"label":"Affected pregnancy","value":"AFFECTED_PREGNANCY"},
                {"label":"Pregnancy (LMP known)","value":"PREGNANCY_LMP"},
                {"label":"Spontaneous miscarriage","value":"SAB"},
                {"label":"Spontaneous miscarriage, affected","value":"SAB_AFFECTED"},
                {"label":"Termination of pregnancy","value":"TOP"},
                {"label":"Termination, affected","value":"TOP_AFFECTED"},
                {"label":"Ectopic pregnancy","value":"ECTOPIC"},
                {"label":"Stillbirth","value":"STILLBIRTH"}] },
  { "name": "gestationalWeeks", "label": "Gestational weeks", "type": "NUMBER", "required": false },
  { "name": "lmpDate", "label": "Last menstrual period date", "type": "DATE", "required": false }
]}
```

### `assisted_reproduction` (shape: `object`, matches `AssistedReproductionAnswerWriter.Entry`)
```json
{ "shape": "object", "fields": [
  { "name": "reproductionType", "label": "Type of assisted reproduction", "type": "DROPDOWN", "required": true,
    "options": [{"label":"Donor","value":"DONOR"},{"label":"Sperm donor","value":"SPERM_DONOR"},
                {"label":"Egg donor","value":"EGG_DONOR"},{"label":"Gestational carrier","value":"GESTATIONAL_CARRIER"},
                {"label":"Surrogate","value":"SURROGATE"},{"label":"Intended parent","value":"INTENDED_PARENT"},
                {"label":"IVF","value":"IVF"},{"label":"IUI","value":"IUI"},{"label":"ICSI","value":"ICSI"},
                {"label":"Embryo adoption","value":"EMBRYO_ADOPTION"},{"label":"Anonymous donor","value":"ANONYMOUS_DONOR"}] },
  { "name": "anonymous", "label": "Was a donor/carrier anonymous?", "type": "DROPDOWN", "required": false,
    "options": [{"label":"Yes","value":true},{"label":"No","value":false}] },
  { "name": "participants", "label": "People involved (leave empty if anonymous)", "type": "GROUP_LIST", "required": false,
    "fields": [
      { "name": "role", "label": "Role", "type": "DROPDOWN", "required": true,
        "options": [{"label":"Egg donor","value":"EGG_DONOR"},{"label":"Sperm donor","value":"SPERM_DONOR"},
                    {"label":"Gestational carrier","value":"GESTATIONAL_CARRIER"},
                    {"label":"Intended parent","value":"INTENDED_PARENT"},
                    {"label":"Donated to","value":"DONATED_TO"},{"label":"Carried for","value":"CARRIED_FOR"}] },
      { "name": "familyMemberId", "label": "Family member", "type": "FAMILY_MEMBER_SELECT", "required": true }
    ]}
]}
```

## 6. Frontend work

- `QuestionField.jsx`: add a `STRUCTURED` branch. Refactor the existing TEXT/NUMBER/DATE/DROPDOWN
  rendering into small reusable sub-field components first, so the new branch calls the same code
  rather than duplicating it (the existing RADIO/CHECKBOX-only top-level branches stay as they are
  — unaffected).
- New components: a field-by-`fields[]` renderer, a `GROUP_LIST` repeatable-row wrapper (add/remove
  row), and `FamilyMemberSelect`/`FamilyMemberMultiSelect` backed by `client.getFamilyMembers`.
- Value assembly: build the exact object/array shape from §5 client-side, then submit through the
  existing `submitAnswer` call unchanged — no API contract change on the submit side, only on what
  `GET current-template` now also returns (`fieldConfig`).
- The "does this apply? Yes/No" gate (§4.2) wraps every `shape: "object"` STRUCTURED field.

## 7. The `country_of_origin` fix (found during this pass, unrelated to the seven above)

Migration 028 (this session) changed `person.country_of_origin`/`family_member.country_of_origin`
from `VARCHAR` to `TEXT[]`, and `PersonAnswerWriter`/`FamilyMemberAnswerWriter` now call
`stringArray(value)`, which requires a JSON array. The seeded question is still `type: TEXT`
(`QuestionField.jsx`'s TEXT input submits a plain string) — submitting it today throws
`InvalidAnswerException`. Fix: reseed it as `CHECKBOX` with `options` = the full ISO 3166-1
alpha-2 country list (same pattern already used for `race`/`ethnicity`, no new question type
needed). Builder's call on where to source the ~195-entry list (a small constant, not worth a new
table).

## 8. What's left to the builder

- Exact migration file naming/number (next available Liquibase changeset id).
- The ISO country list literal for §7.
- Minor copy wording on labels/the Yes/No toggle.
- Whether `FamilyMemberSelect`/`FamilyMemberMultiSelect` exclude the subject itself from the list
  (it should — a person can't be their own co-twin/adoptive parent/participant) — implementer's
  call, not expected to be controversial.
- Test coverage: one integration test per question exercising the real writer through the full
  `submitAnswer` → `STRUCTURED` path (not just unit-testing the writers, which are already covered),
  plus a frontend test per new component.
