# Pedigree JSON Export

| | |
|---|---|
| **Status** | v2 as of 2026-10-30: `conditions[]`, `geneticTests[]`, the document-level `diseases[]` catalog, `twinship`, `adoption`, `pregnancyOutcome`, `assistedReproduction`, and `genderTransition` are now all wired up and live-verified (see "v2 scope" below) — `DISEASE`/`MUTATION`/`DISEASE_CATALOG`/`TWINSHIP`/`TWINSHIP_MEMBER`/`ADOPTION`/`ADOPTION_PARENT`/`PREGNANCY_OUTCOME`/`ASSISTED_REPRODUCTION`/`ASSISTED_REPRODUCTION_PARTICIPANT` are all read, not just PERSON/FAMILY_MEMBER/relationship tables as in v1 (2026-10-14). The generation-algorithm explanation below's "`FAMILY_MEMBER.relationshipType = SPOUSE`" reference is historical — that column was removed 2026-10-23, replaced by `family_member_role`; the code has used `FamilyMemberRole` since then. |
| **Endpoint** | `GET /api/v1/assessment/{sessionId}/pedigree-export` |
| **Implementation** | `PedigreeExportService.java` (engine/mapping logic), `PedigreeExportController.java` (thin controller) |
| **Target format** | `pedigree_schema.json` (draft 2020-12, `schemaVersion: "4.0.0"`, PSWG/Bennett 2022+2025) — the format the separately-built "PedigreeDraw" tool consumes |
| **Context** | This is "task 4" from the manager's 2026-09-28 four-task assignment. Every piece of work since then — the relationship tables, subject-scope execution, the sex/gender model, the demographic/relationship question set — was prerequisite groundwork for this export, not the export itself. |

---

## What it does

Given a `sessionId`, loads that session's proband (`PERSON`) plus every `FAMILY_MEMBER`, `PARENT_CHILD_RELATIONSHIP`, and `PARTNER_RELATIONSHIP` row scoped to them, builds an in-memory family graph, and produces a JSON document matching `pedigree_schema.json`'s `individuals` map — one entry per person, keyed by a small sequential integer id the schema requires (not our internal UUIDs).

It's a read-only derived view (`GET`), available regardless of session status — same reasoning as `/responses`: reading a snapshot doesn't require the session to be finished.

## Graph algorithm — the parts the schema itself doesn't specify

`pedigree_schema.json` defines the document's shape but not how to compute `generation`, `individualLabel`, or how `children{}`/`halfsiblings{}` are keyed. These conventions were reverse-engineered from the populated reference sample (`family_of_10_newschema_V4.json`), not invented:

- **`generation`**: individuals who are married to each other (a `PARTNER_RELATIONSHIP` row, or `FAMILY_MEMBER.relationshipType = SPOUSE`) form a "generation unit" — confirmed by the sample, where a spouse with no known parents of their own (`Linda Miller`, `Emily Carter`, `David Wilson`) still gets their partner's generation, not generation 0 by default. Generation is then the longest path from a root unit (no known parent edges) via parent-child edges between units, computed with Kahn's-algorithm-style topological layering (not plain recursion) specifically so a cycle in bad data can't cause a stack overflow — any node left unresolved after the queue drains defaults to generation 0 rather than crashing the export.
- **`individualLabel`**: `{roman numeral of generation+1}-{sequence within generation}`, e.g. `II-3`. Sequence is assigned by ordering individuals within a generation by birth date (nulls last), then record creation time as a tiebreak. The sample's twin letter-suffixing (`III-4a`/`III-4b`) is **not reproduced** — it needs `TWINSHIP` data, which doesn't exist in this codebase (Section G).
- **`children{}`/`halfsiblings{}`**: both keyed by "the other relevant individual's id" (co-parent for children, shared parent for half-siblings), exactly as the sample does. When a child's other parent isn't known, this implementation buckets them under the known parent's **own** id as a "no identified co-parent" bucket — a convention of this implementation, not something the sample demonstrates (every child there has two known parents).
- **`siblings[]`/half-sibling detection**: derived from shared `parentsOf` sets (full sibling = identical non-empty parent set; half-sibling = exactly one shared parent, sets otherwise differ) — matches the entity-level decision already recorded in `ParentChildRelationship.java`'s own comment ("siblings/half-siblings are deliberately NOT stored - derived at export time").

## v2 scope

| Area | What happens |
|---|---|
| `conditions[]` | Built from every `DISEASE` row owned by the individual (`code.system`/`code.id`/`code.label`, `ageAtDiagnosis`, `laterality`, `status`). `diseaseRefId` is set when that row's `DISEASE_CATALOG` entry made the document-level top-4 cut (see below), `null` otherwise. |
| `geneticTests[]` | Built from every `MUTATION` row owned by the individual (`gene`, `result`, `variant`, `testType`). |
| `affectedAndCarrier` | Derived from `conditions[].length`: 0 → `unaffected`, 1 → `affected`, 2+ → `affectedMultiple`. Carrier flags (`carrierHet`/`asymptomaticCarrier`/`obligateCarrier`) are **not** derived — that needs a rule from genetic-test results that hasn't been designed yet (§6.2 in `QP-Pedigree-Schema-Design.md`, still open). |
| Document-level `diseases[]` catalog | Every distinct `DISEASE_CATALOG` entry referenced anywhere in this pedigree gets one entry, up to the schema's hard cap of 4, in first-encountered order (individuals walked in generation/export-id order). Assigned a quadrant clockwise (`upperLeft`/`upperRight`/`lowerRight`/`lowerLeft`) and the catalog's own color/abbreviation, falling back to the catalog name and `#808080` when those are unset. The schema's 5th quadrant value, `centerDot`, is never assigned — no rule exists yet for when a disease should render as a center dot instead of a quadrant. |
| `twinship` | Built from the individual's `TWINSHIP_MEMBER` row (if any): `type` from the group's `TWINSHIP.twin_type`, `coTwins` from every other member of that same group, resolved to their export ids. `null` when the individual isn't in any twin group. |
| `adoption` | Built from the individual's `ADOPTION` row (if any): `type` from `adoption_type`, `adoptiveParents` from `ADOPTION_PARENT` rows resolved to export ids. `biologicalParents` is **not** read from any adoption-specific storage — it's derived from the same `parentsOf` graph used for the individual's own `parents[]` field (`ADOPTION_PARENT` only ever stores adoptive parents; biological ones are already captured via `PARENT_CHILD_RELATIONSHIP`/`FAMILY_MEMBER_ROLE`, the same source `parents[]` itself reads — storing them twice would risk the two disagreeing). `null` when the individual has no `ADOPTION` row. |
| `pregnancyOutcome` | Built from the individual's `PREGNANCY_OUTCOME` row (if any) — only ever applies to a `FAMILY_MEMBER`, never the proband, matching that table's own `NOT NULL family_member_id`. `null` otherwise. |
| `assistedReproduction` | Built from the individual's `ASSISTED_REPRODUCTION` row (if any): `type`, `anonymous`, and every `ASSISTED_REPRODUCTION_PARTICIPANT` resolved by role to `eggDonorId`/`spermDonorId`/`gestationalCarrierId` (single) or `intendedParents`/`donatedTo`/`carriedFor` (arrays), each as export ids. `null` otherwise. |
| `genderTransition` | Built from the individual's `GENDER_TRANSITION` row when one exists (`fromGender`/`toGender` via the same gender-code mapping as the main `gender` field, `transitionYear`). **Omitted entirely** when absent (it's optional in the schema, not nullable — same "omitted, not null" distinction as `settings`/`_coverageMap` below). |
| `extensions.cancerRiskModel` | Always `{}` (empty `extensions` object) | `CANCER_RISK_PROFILE` doesn't exist — scope still pending a decision on whether it's in Phase 1. |
| `ancestry.inferredAncestry` | Always `null` | No genetic ancestry inference is performed. |
| `settings` / `_coverageMap` | Omitted entirely (not even present as `null`) | Both are optional in the schema's own top-level `required` list — confirmed against the actual schema file. |
| A family member with no created relationship edge (e.g. an `EXTENDED_FAMILY` member added without a follow-up `POST .../relationships/parent-child` call) | Exports as a disconnected node — empty `parents`/`spouses`/`children`, generation 0 | Honest reflection of the data: `QP-Pedigree-Demographic-Questions.md` already documents that answering a question never auto-creates a relationship edge. |

Every one of these was previously `null`/empty/hardcoded in v1 (2026-10-14) — `DISEASE`/`MUTATION` existed but used the pre-pedigree shape, and `TWINSHIP`/`ADOPTION`/`PREGNANCY_OUTCOME`/`ASSISTED_REPRODUCTION`/`GENDER_TRANSITION`/`DISEASE_CATALOG` didn't exist at all. All of it is now read live, not hardcoded.

## `matingLineCode` — provisional, pending your mentor's decision

`spouseEntry.matingLineCode` is a single required field with only one value per couple (`matingLine`/`consanguineous`/`separated`/`divorced`/`noLongerTogether`/`casualOrUnknown`) — it's the literal pedigree-rendering line-style code (`pedigree-rendering-legend-pswg2022_simplify.json`'s `matingLineCode`), not a general-purpose field. Since `PARTNER_RELATIONSHIP.relationshipStatus`/`.consanguineous` are now captured independently (2026-10-14's mentor-review fix), a couple can be both married *and* consanguineous in our data — but the export can only render one line style.

**Current rule** (isolated in `PedigreeExportService.deriveMatingLineCode()`, a single method so swapping it later is a one-line change, not a re-architecture):

```
consanguineous = true                        -> "consanguineous"   (wins over everything else)
relationshipStatus = SEPARATED (and not consanguineous)  -> "separated"
relationshipStatus = DIVORCED  (and not consanguineous)  -> "divorced"
otherwise -> fall back to the stored matingLineType (direct-API callers), default "matingLine"
```

This was explicitly flagged to the user as unresolved; her answer (2026-10-14): ask her mentor, and in the meantime either skip it or use the recommended rule above. The recommended rule was used. **When the mentor's answer comes back, update `deriveMatingLineCode()` only — nothing else in the export depends on this choice.**

## Verification

- `PedigreeExportServiceTest` (9 tests). The original 2 (v1): a 3-generation graph (grandparents → parents → proband + full sibling + half-sibling), covering a consanguineous couple (the provisional `matingLineCode` rule), a half-sibling (`halfsiblings{}` keying), a spouse who "married in" with no known parents of their own (generation-unit propagation), a deceased individual with `age_at_death`, and a relationship-edge-less family member exporting as a valid disconnected node. The 7 added for v2: `conditions[]`/`geneticTests[]`/`affectedAndCarrier` derivation together with the document-level `diseases[]` catalog's first entry; the catalog capping at 4 distinct entries with clockwise quadrants (a 5th disease's condition still exports, just with `diseaseRefId: null`); `genderTransition` present-vs-omitted; `twinship` with `coTwins` correctly excluding the individual themselves; `adoption` with `adoptiveParents` from `ADOPTION_PARENT` and `biologicalParents` independently verified to match the same individual's own `parents[]`; `pregnancyOutcome`; `assistedReproduction` participant-role resolution to export ids.
- Live-verified against the real running app (2026-10-14, v1 scope): proband + mother + father walked through the real question flow, linked via a real `POST .../relationships/partners` call, exported, and the resulting document manually cross-checked field-by-field against `pedigree_schema.json`'s `required` lists — no missing required fields, no stray `additionalProperties: false` violations. The v2 additions were verified via the automated tests above (the question content needed to drive these fields end-to-end through the actual UI doesn't fully exist yet — see `QP-Pedigree-Mapping-Specification.md`'s question-set gap).
- No JSON-Schema-validation library exists in this project yet (no dependency for one), so conformance was checked manually against the schema file rather than programmatically — worth revisiting if a validation library is ever added.

## Known gaps, honestly stated

- No request-time filtering (e.g. "only export back to generation N") — always exports the full known graph.
- `name` is `firstName + " " + lastName` only — `middleName`/`preferredName` aren't incorporated.
- `birthYear`/`age`/`deathYear` derivation prefers the real `birthDate` column when present, falling back to the directly-answered `age`/`age_at_death` facts (the latter stored in `additional_facts`, not a dedicated column — see `QP-Pedigree-Demographic-Questions.md`'s "What changed from v1" section).
