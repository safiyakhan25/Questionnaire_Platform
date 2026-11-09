# QP → Pedigree Mapping Specification

| | |
|---|---|
| **Document status** | Partially implemented — see each row's Status. No longer "nothing built from this document": `PedigreeExportService`/`PedigreeExportController` (built ~2026-10-14, extended after 2026-10-23) already implement a meaningful slice of §5/§6/§11, ahead of where v2 of this document said things stood. |
| **Purpose** | For every field in `pedigree_schema.json`, define where it comes from in QP, whether a new question is needed, how it's transformed, and its implementation status |
| **Depends on** | `QP-Pedigree-Schema-Design.md` (v3) for entity/column names; supersedes nothing there, just operationalizes it field-by-field |
| **Note** | Re-verified 2026-10-25 directly against the running code (`PedigreeExportService.java`, `FamilyMemberService.java`, `DiseaseAnswerWriter.java`, `MutationAnswerWriter.java`, `AnswerValidator.java`, Liquibase changesets 010-018) and against `pedigree_schema.json` v4.0.0 + `family_of_10_newschema_V4.json` + `family_of_15_newschema_V4.json` + `pedigree-rendering-legend-pswg2022_simplify.json` directly, not only against the earlier design docs. Changes from that re-verification are marked **(2026-10-25)** inline. |
| **Revision** | v3 (2026-10-25) — corrects every row that code has since overtaken or contradicted (see the new §0), without re-deciding anything already decided in v2. v2 (2026-09-30) incorporated a mentor review of v1: added the `Omitted` status category, resolved the gender/sex rows, tightened the AMAB/AFAB rule, reframed `external`/`notExamined` and `CANCER_RISK_PROFILE` (§10), and updated the closing summary. |

---

## 0. What changed since v2 — read this first

v2 was written 2026-09-30/10-14, before `PedigreeExportService` existed in its current form and before the 2026-10-23 `family_member_role` redesign. Re-checking this document against the actual code on 2026-10-25 found four kinds of drift:

1. **Marked `New`/`Open decision` in v2, actually implemented now** — the relationship-graph fields in §11 (`parents[]`, `siblings[]`, `halfsiblings{}`, `children{}`, `spouses{}.matingLineCode` via a provisional derivation rule, `generation`, `individualLabel`) are fully computed today in `PedigreeExportService`, and answer-level persistence for `conditions[]`/`geneticTests[]` already flows into `DISEASE`/`MUTATION` rows via `DiseaseAnswerWriter`/`MutationAnswerWriter` — just still in the **pre-pedigree column shape**, not yet reshaped. See the updated rows below, each tagged **(2026-10-25)**.
2. **A structural change v2 didn't know about**: `FAMILY_MEMBER.relationship_type` was removed 2026-10-23 and replaced by a new `family_member_role` table (one row per role, keyed by scope), so a person can hold more than one true relational label once recursive adding is allowed. Every §11 row that referenced `FAMILY_MEMBER.relationship_type` is corrected below. See `QP-Family-Relationship-Persistence-Design.md` for the full decision record.
3. **Code made a silent call where this document left an open decision** — `PedigreeExportService` currently emits `external: false`, `donor: false`, `notExamined: false` for every individual (never omits them), even though §3/§6.4 frame `external`/`notExamined` as "open, leaning not to add at all." That's not a contradiction at the *data model* level (no column was added), but it is one at the *export output* level — flagged in §3/§6 below as something to either ratify or fix, not silently leave inconsistent.
4. **A real, undocumented implementation detail**: there is no dedicated `age_at_death` column anywhere. `PersonAnswerWriter`/`FamilyMemberAnswerWriter` have no case for that answer key, so it lands in the generic `additional_facts` JSONB bucket, and `PedigreeExportService` reads it back out of there. This has worked in practice (it's covered by `PedigreeExportServiceTest`) but was never written down in either design doc — see §3.

See §16 and §17 (new) for items found 2026-10-25 that weren't in v2 at all: unbuilt API endpoints the 2026-10-23 design doc already called for, and this implementation-detail list in one place.

## Status legend (7 categories — `Omitted` added in v2, everything else unchanged)

| Status | Meaning |
|---|---|
| **Existing** | QP already stores this, or can derive it by a simple transform of one existing column. No schema change needed. |
| **Modify** | An existing QP column/entity needs to actually change shape to support this. |
| **New** | Nothing in QP captures this today — needs a new entity/column, per `QP-Pedigree-Schema-Design.md` (or, where noted, newly identified in this document). |
| **Computed** | Never stored — derived at export time from other stored data (usually the relationship graph or an aggregation across rows). |
| **Always null** | QP will never produce real data for this field, but the field *is* nullable per the schema — safe to emit as `null`. |
| **Omitted** | The field is **not nullable** in the schema (no `null` in its `type`), so it must be left out of the output entirely rather than set to `null`. Emitting `null` here would fail schema validation. |
| **Open decision** | Blocked on an unresolved decision — do not implement until resolved. |

"Question needed?" = does the questionnaire need a new admin-configured `QUESTION` to collect the underlying data (as opposed to: already collected, computed from other data, or never collected).

---

## 1. Top-level document fields

| Pedigree field | QP source | Question needed? | Transformation | Status |
|---|---|---|---|---|
| `schemaVersion` | — | No | Hardcoded constant (`"4.0.0"`) | Existing |
| `schemaCompliance` | — | No | Hardcoded constant | Existing |
| `title` | — | No | Export-time parameter (e.g. proband name + "Pedigree") | Computed |
| `description` | — | No | Export-time parameter, optional | Computed |
| `header` | — | No | Export-time parameter, optional | Computed |
| `footer` | — | No | Export-time parameter, optional | Computed |
| `createdDate` | — | No | Current timestamp at export | Computed |
| `_coverageMap` | — | No | Internal tooling metadata, not ours to produce, and not nullable per schema — key left out entirely | Omitted |
| `settings` (entire block — ~60 sub-fields: rendering, pagination, output, validation, determinism) | — | No | Pure PedigreeDraw renderer configuration (fonts, colors, layout, page size, PNG/PDF/SVG options). None of it maps to anything QP collects, and it's not nullable per schema — key left out entirely rather than enumerated field-by-field. See `QP-Pedigree-Schema-Design.md` §4. | Omitted |
| `diseases[]` (the array itself) | `DISEASE_CATALOG` | No | Select ≤4 catalog entries actually present in this family's `DISEASE` rows | Computed *(selection rule is Open decision — see §9)* |

---

## 2. `diseases[]` — catalog entry (per array item)

| Pedigree field | QP source | Question needed? | Transformation | Status |
|---|---|---|---|---|
| `id` | — | No | Sequential integer assigned at export time (1–4), not the DB UUID | Computed |
| `name` | `DISEASE_CATALOG.name` | No | Direct copy | New *(table doesn't exist yet)* |
| `abbreviation` | `DISEASE_CATALOG.abbreviation` | No | Direct copy | New |
| `color` | `DISEASE_CATALOG.default_color` | No | Direct copy | New |
| `quadrant` | — | No | Assigned in order of appearance, clockwise from upper-left, capped at 4 | Computed *(the >4-disease rule is Open decision — see §9)* |

---

## 3. `individual` — identity & demographics

| Pedigree field | QP source | Question needed? | Transformation | Status |
|---|---|---|---|---|
| `id` | `PERSON.id` / `FAMILY_MEMBER.id` | No | Reindexed to sequential integers at export time (pedigree IDs are `1,2,3...`, ours are UUIDs) | Computed |
| `name` | `PERSON.first_name`+`last_name` / same on `FAMILY_MEMBER` | Yes *(already asked for `PERSON`, needs asking for each `FAMILY_MEMBER` once family-member collection exists — task 3)* | Concatenate first + last | Existing |
| `individualLabel` | relationship graph | No | Roman-numeral generation + left-to-right position (`II-3`), or `ED-`/`GC-` prefix for donors/gestational carriers | Computed |
| `generation` | relationship graph | No | Graph depth from whichever individuals have no recorded parent edges | Computed |
| `external` | — (no column proposed) | No | Optional per schema — **default is not to add this field to QP at all** unless a concrete business need for "individual outside the direct pedigree" shows up. See `QP-Pedigree-Schema-Design.md` §3.1/§6.4. **(2026-10-25)** The *data model* decision is still correctly open (no column exists), but `PedigreeExportService.buildIndividual()` already hardcodes `external: false` in every exported document — i.e. the export layer has already picked "always emit `false`," which is a real (if minor) answer to the open question, just not one anyone decided on purpose. Ratify it explicitly or change it to omit the key — don't leave it as an accidental default. | Open decision *(leaning: omit; code currently emits `false` instead — see note)* |
| `donor` | `ASSISTED_REPRODUCTION_PARTICIPANT` | No | `true` if this individual has a participant row with role `EGG_DONOR`/`SPERM_DONOR` | Computed *(2026-10-25: hardcoded `false` for everyone today, since `ASSISTED_REPRODUCTION_PARTICIPANT` doesn't exist yet — consistent with §15, not a bug)* |
| `notExamined` | — (no column proposed) | No | Optional per schema — **default is not to add this field to QP** unless the questionnaire actually needs to distinguish "known unaffected" from "not personally examined." See `QP-Pedigree-Schema-Design.md` §3.1/§6.4. **(2026-10-25)** Same accidental-default situation as `external` above — the export hardcodes `notExamined: false` for everyone. | Open decision *(leaning: omit; code currently emits `false` instead — see note)* |
| `birthYear` | `PERSON.birth_date` / `FAMILY_MEMBER.birth_date` | Yes *(existing for `PERSON`, needed per family member)* | Extract year | Existing — **implemented in `PedigreeExportService`, 2026-10-25 verified**: prefers `birth_date`, falls back to nothing (no stored birth year without a date) |
| `age` | `PERSON.age` / `FAMILY_MEMBER.age`, or computed from `birth_date` | Yes | Direct copy, or computed if not directly answered | Existing — **implemented, 2026-10-25 verified**: prefers computing from `birth_date` (`Period.between(...)`), falls back to the directly-answered `age` fact |
| `deathYear` | `PERSON.deceased_date_time` / `FAMILY_MEMBER.deceased_date_time` | Yes | Extract year | Existing — **implemented, 2026-10-25 verified**: prefers `deceased_date_time`, falls back to `birth_date.year + age_at_death`. **Undocumented until now**: `age_at_death` has no dedicated column on `PERSON`/`FAMILY_MEMBER` — `PersonAnswerWriter`/`FamilyMemberAnswerWriter` have no case for that answer key, so it's written into the generic `additional_facts` JSONB bucket like any other unrecognized key, and `PedigreeExportService.readAgeAtDeath()` parses it back out from there. Works today (covered by `PedigreeExportServiceTest`), but if `age_at_death` becomes a question asked often, it should probably graduate to a real column — tracked here, not acted on. |

---

## 4. `individual` — sex, gender, transition

**Resolved (2026-09-30)** — confirmed by mentor review, no longer open. `PERSON`/`FAMILY_MEMBER.gender_at_birth` is removed; three fields replace it. See `QP-Pedigree-Schema-Design.md` §3.2 for the full rationale.

| Pedigree field | QP source | Question needed? | Transformation | Status |
|---|---|---|---|---|
| `sex` | `sex_assigned_at_birth` (3-value enum, replaces `gender_at_birth` — **implemented 2026-10-14**) | Yes — seeded as `DEMOGRAPHICS` Q7 | Direct mapping: `UNKNOWN→0, MALE→1, FEMALE→2` | ✅ Modify (done) |
| `gender` | `gender_identity` (type changed from free-text VARCHAR to a 4-value enum — **implemented 2026-10-14**) | Yes — seeded as `DEMOGRAPHICS` Q8 | Direct mapping: `MAN→1, WOMAN→2, NONBINARY→3, UNKNOWN→4` | ✅ Modify (done) |
| `genderTransition.fromGender` / `.toGender` / `.transitionYear` | `GENDER_TRANSITION` | Yes | Direct copy, `fromGender`/`toGender` use the same 4-value `gender_identity` enum above | New *(table not built — Section G scope)* |
| *(intersex status)* | `is_intersex` (boolean — **implemented 2026-10-14**) | Yes — seeded as `DEMOGRAPHICS` Q9 | Feeds `individualAnnotation.intersexAnnotation` (§6), not `sex`/`gender` directly | ✅ New (done) |

---

## 5. `individual` — life status

| Pedigree field | QP source | Question needed? | Transformation | Status |
|---|---|---|---|---|
| `lifeStatus` | `PERSON.deceased` / `FAMILY_MEMBER.deceased` (nullable Boolean) | Existing question | `null→"unknown", false→"alive", true→"deceased"` — the existing nullable Boolean already has exactly 3 states, no schema change needed | Existing — **implemented in `PedigreeExportService`, 2026-10-25 verified**, exact mapping above |
| `role` | `PERSON` vs `FAMILY_MEMBER` | No | `PERSON`→`"proband"`; every `FAMILY_MEMBER`→`"none"`. `"consultand"` unsupported. | Computed — **implemented, 2026-10-25 verified**, exact mapping above |

---

## 6. `individual` — affectation & annotation

| Pedigree field | QP source | Question needed? | Transformation | Status |
|---|---|---|---|---|
| `affectedAndCarrier` — `unaffected`/`affected`/`affectedMultiple` | count of linked `DISEASE` rows | No | 0→`unaffected`, 1→`affected`, 2+→`affectedMultiple` | Computed *(not wired up yet — **2026-10-25**: `PedigreeExportService` currently hardcodes `["unaffected"]` for every individual regardless of their actual `DISEASE` rows, because `DISEASE` hasn't been reshaped to carry a `status`/ontology `code` the export can trust yet — see §7. The rule itself is simple to add once §7's `DISEASE` reshape lands; it is not blocked on anything else.)* |
| `affectedAndCarrier` — `carrierHet`/`asymptomaticCarrier`/`obligateCarrier` | `MUTATION`/`GENETIC_TEST` results | No | Derived from genetic test result codes (e.g. heterozygous pathogenic finding → `carrierHet`) — exact derivation rule not yet designed | **Open decision** *(new — the derivation rule itself needs defining once genetic-test data exists)* |
| `affectedAndCarrier` — `unknownStatus`, `notExamined` | `not_examined` flag / absence of data | No | Direct/computed | **Open decision** *(depends on whether `not_examined` gets added at all — see §3, `QP-Pedigree-Schema-Design.md` §6.4)* |
| `individualAnnotation` — `amabAnnotation`/`afabAnnotation` | `sex_assigned_at_birth` + `gender_identity` | No | Precise deterministic rule (tightened per mentor review — "computed when the two diverge" was too broad): `MALE + WOMAN → amabAnnotation`; `FEMALE + MAN → afabAnnotation`; any other combination (including anything involving `UNKNOWN` or `NONBINARY`) → no annotation | Computed |
| `individualAnnotation` — `intersexAnnotation` | `is_intersex` | No | Direct | ✅ New (done) |
| `individualAnnotation` — `testedNegative`/`testedPositiveUnaffected` | `MUTATION`/`GENETIC_TEST.result` | No | Derived from test result | Computed *(once genetic tests exist)* |
| `individualAnnotation` — `evaluatedNormal` | — | No | No QP concept matches this ("personally evaluated, clinically unaffected" implies a clinician exam, not a self-report questionnaire) | Always null |
| `individualAnnotation` — `notExamined` | same as `affectedAndCarrier.notExamined` above | No | Direct | **Open decision** *(same dependency as above)* |

---

## 7. `individual.conditions[]`

**(2026-10-25)** Answer-level persistence for this is already wired and working end to end — `DiseaseAnswerWriter` (new since v2) takes the `"diseases"` answer key's JSON array and fully reconciles it into `DISEASE` rows on every submit (delete-all-then-reinsert, so edits and clears both work for free). What's *not* done is the table reshape itself: `DISEASE` today is still exactly its pre-pedigree shape (`disease_id`, `person_id`/`family_member_id` XOR, `disease_type` VARCHAR, `age_at_diagnosis`) — no `disease_catalog_id`, no `code_system`/`code_id`/`code_label`, no `laterality`, no `status`. So every row below is still genuinely `New` at the schema level, but the *answer-writing plumbing* that will consume the reshaped columns already exists and won't need to be built from scratch — only `DiseaseAnswerWriter.parseEntries()`/`applyToPerson()`/`applyToFamilyMember()` need extending to read/write the new fields once they exist.

| Pedigree field | QP source | Question needed? | Transformation | Status |
|---|---|---|---|---|
| `diseaseRefId` | `DISEASE.disease_catalog_id` | No | Populated only when this diagnosis's catalog entry is one of the ≤4 featured in this export | Computed *(depends on §9)* |
| `code.system` | `DISEASE.code_system` | Indirect *(see §12 proposal)* | Direct copy | New |
| `code.id` | `DISEASE.code_id` | Indirect | Direct copy | New |
| `code.label` | `DISEASE.code_label` | Indirect | Direct copy | New |
| `ageAtDiagnosis` | `DISEASE.age_at_diagnosis` | Yes | Direct copy | **Existing column, already being written** by `DiseaseAnswerWriter` today — just needs no further schema change, only to survive the reshape |
| `laterality` | `DISEASE.laterality` | Yes, where relevant | Direct copy | New |
| `status` | `DISEASE.status` | Yes | Direct copy | New |

---

## 8. `individual.geneticTests[]`

**(2026-10-25)** Same situation as §7: `MutationAnswerWriter` (new since v2) already reconciles the `"genetic_findings"` answer key's JSON array into `MUTATION` rows on every submit, but `MUTATION` itself is still its pre-pedigree shape (`mutation_id`, `person_id`/`family_member_id` XOR, `gene` VARCHAR, `mutation` free-text VARCHAR) — the `result`/`variant`/`test_type` reshape from `QP-Pedigree-Schema-Design.md` §2.5 hasn't happened yet. `gene` is already being written correctly as-is.

| Pedigree field | QP source | Question needed? | Transformation | Status |
|---|---|---|---|---|
| `gene` | `MUTATION.gene` | Yes | Direct copy | **Existing column, already being written** by `MutationAnswerWriter` today |
| `result` | `MUTATION.result` | Yes | Direct copy | Modify *(existing table, reshaped per `QP-Pedigree-Schema-Design.md` §2.5 — replaces the free-text `mutation` column)* |
| `variant` | `MUTATION.variant` | Yes, optional | Direct copy | Modify |
| `testType` | `MUTATION.test_type` | Yes, optional | Direct copy | Modify |

---

## 9. `individual.ancestry`

| Pedigree field | QP source | Question needed? | Transformation | Status |
|---|---|---|---|---|
| `selfReportedEthnicity` | `PERSON.ethnicity[]` / `FAMILY_MEMBER.ethnicity[]` | Existing question | Direct copy | Existing |
| `countryOfOrigin` | `country_of_origin` (single ISO alpha-2 `VARCHAR`, **not an array** — implemented 2026-10-14) | Yes | Direct copy, wrapped in a single-element array at export time if the target field expects an array | New |
| `inferredAncestry` | — | No | Requires genetic ancestry inference (gnomAD population analysis) we don't perform | Always null |

---

## 10. `individual.extensions.cancerRiskModel` — scope not yet decided

**Not covered in `QP-Pedigree-Schema-Design.md` v1 or v2 — missed entirely in both earlier passes, then added to the design doc as `CANCER_RISK_PROFILE` (§2.11, v3) with explicit pending-scope framing per mentor review.** Feeds CanRisk/BOADICEA cancer-risk export tools. **Do not mark this `New` and build it** — the open question is whether this data is part of QP's required collection scope at all, or only relevant to a later CanRisk/BOADICEA integration separate from the questionnaire itself. See `QP-Pedigree-Schema-Design.md` §6.4.

| Pedigree field | QP source | Question needed? | Transformation | Status |
|---|---|---|---|---|
| `pathology.ER`/`.PR`/`.HER2`/`.CK14`/`.CK56` | proposed `CANCER_RISK_PROFILE` table | Yes, if in scope | Direct copy, each `positive`/`negative`/null | **Open decision** *(pending mentor scope call: Phase 1 vs Phase 2)* |
| `mmrIHC.MLH1`/`.MSH2`/`.MSH6`/`.PMS2` | same table | Yes, if in scope | Direct copy, each `intact`/`lost`/`equivocal`/null | **Open decision** *(same)* |

---

## 11. `individual` — relationship fields

**(2026-10-25) Structural correction, not just a status update:** every row below that said `FAMILY_MEMBER.relationship_type` is now wrong at the column level — that column was dropped 2026-10-23 and replaced by a new `family_member_role` table (one row per `{family_member_id, scope_person_id | scope_family_member_id, relationship_type}`), because the same real person can now hold more than one true relational label once recursive adding (any family member can itself be the anchor for adding further relatives, not just the proband) is allowed. See `QP-Family-Relationship-Persistence-Design.md` for the full decision record — read that document, not just this row, before touching relationship code. Every row's "QP source" below is corrected to `FAMILY_MEMBER_ROLE`.

**Also (2026-10-25):** this entire section's `Computed` claims are no longer aspirational — `PedigreeExportService` (built after v2, extended after the 2026-10-23 redesign) implements all of `parents[]`, `siblings[]`, `halfsiblings{}`, `children{}`, `spouses{}` (including a working, if provisional, `matingLineCode` derivation), `generation`, and `individualLabel` today, verified by reading the service directly and by `PedigreeExportServiceTest`. None of this is still "to build" at the graph-logic level — what's still open is only the specific `matingLineCode` derivation *rule* (below) and the API-level gaps in the new §16.

| Pedigree field | QP source | Question needed? | Transformation | Status |
|---|---|---|---|---|
| `parents[]` | `FAMILY_MEMBER_ROLE` (`MOTHER`/`FATHER` roles scoped to the proband — stays implicit, never an explicit `PARENT_CHILD_RELATIONSHIP` row, since the proband can never be "the child" in that table) + `PARENT_CHILD_RELATIONSHIP` (everyone else's, now **auto-created** by `FamilyMemberService` for `SON`/`DAUGHTER`/`ADOPTED_SON`/`ADOPTED_DAUGHTER`/`MOTHER`/`FATHER` roles at family-member-creation time — a client no longer needs a separate relationship call for these cases) | No *(collected via relationship-type questions)* | Graph lookup | ✅ Implemented and verified 2026-10-25 in `PedigreeExportService.buildParentEdges()` |
| `siblings[]` | derived from shared `PARENT_CHILD_RELATIONSHIP`-equivalent parent edges (via the merged `parentsOf` map built from both `FAMILY_MEMBER_ROLE` and `PARENT_CHILD_RELATIONSHIP`) | No | Two individuals sharing an identical, non-empty parent set | ✅ Implemented and verified 2026-10-25 in `PedigreeExportService.computeFullSiblings()` |
| `halfsiblings{}` | same, sharing exactly one parent, keyed by that shared parent's export id | No | Graph derivation | ✅ Implemented and verified 2026-10-25 in `PedigreeExportService.computeHalfSiblings()` |
| `spouses{}.matingLineCode` | `PARTNER_RELATIONSHIP.mating_line_type`, now overridden at export time by a **provisional derivation rule** (see `QP-Pedigree-Export.md`'s "matingLineCode" section): `consanguineous=true` wins outright → `"consanguineous"`; else `relationship_status=SEPARATED`/`DIVORCED` → `"separated"`/`"divorced"`; else falls back to the stored `mating_line_type` (default `"matingLine"`). This is a real, working rule, not a gap — but it's explicitly flagged in the code itself as **provisional pending your mentor's decision**, isolated in one method (`PedigreeExportService.deriveMatingLineCode()`) specifically so the eventual real answer is a one-method change. | Yes | Direct copy of whichever branch above fires | ✅ **Implemented** 2026-10-25 (corrects v2, which said "nothing derives it yet") — derivation exists and is live, just not final |
| `spouses{}.reproductiveStatus` | `PARTNER_RELATIONSHIP.reproductive_status` | Yes | Direct copy | ✅ Implemented |
| `spouses{}.unshownChildrenCount` | `PARTNER_RELATIONSHIP.unshown_children_count` | Yes, conditional | Direct copy | ✅ Implemented |
| `spouses{}.consanguinityDegree` | `PARTNER_RELATIONSHIP.consanguinity_degree` | Yes, conditional | Direct copy | ✅ Implemented |
| `spouses{}.endedYear` | `PARTNER_RELATIONSHIP.ended_year` | Yes, conditional | Direct copy | ✅ Implemented |
| *(not a pedigree field — richer source data)* | `PARTNER_RELATIONSHIP.relationship_status` / `.consanguineous` (new 2026-10-14) | Yes | Feeds the `matingLineCode` derivation above directly — no longer just "captured for later," it's load-bearing today | ✅ Implemented and actively used |
| `children{}` | inverse of the same merged parent-edge map as `siblings[]`/`halfsiblings{}`, keyed by co-parent id (or by the known parent's own id when the other parent is unrecorded — an implementation convention, not something any reference sample demonstrates) | No | Graph lookup | ✅ Implemented and verified 2026-10-25 in `PedigreeExportService.computeChildrenByCoParent()` |
| *(not a pedigree field — a data-quality behavior)* | `FamilyMemberService.autoAttachSecondParent()` | No | When a child is added under a scope with **exactly one** recorded partner whose `reproductive_status = HAS_CHILDREN`, that partner is auto-attached as the second parent, so "add a sibling under mother's scope" produces a genuine full sibling without re-stating father. **Only evaluated at child-creation time** — if the `PARTNER_RELATIONSHIP` is recorded *after* the children already exist (the actual order in most real walkthroughs), the retroactive backfill described in `QP-Family-Relationship-Persistence-Design.md` §2.11 is **not yet built**. | New gap, found 2026-10-25 *(not in v2 at all — see §16)* |

---

## 12. `individual.twinship`

| Pedigree field | QP source | Question needed? | Transformation | Status |
|---|---|---|---|---|
| `type` | `TWINSHIP.twin_type` | Yes | Direct copy | New |
| `coTwins[]` | `TWINSHIP_MEMBER` (all other members of the same `twinship_id`) | No | Graph lookup | New |

## 13. `individual.adoption`

| Pedigree field | QP source | Question needed? | Transformation | Status |
|---|---|---|---|---|
| `type` | `ADOPTION.adoption_type` | Yes | Direct copy | New |
| `adoptiveParents[]` | `ADOPTION_PARENT` where `parent_role=ADOPTIVE` | No | Graph lookup | New |
| `biologicalParents[]` | `ADOPTION_PARENT` where `parent_role=BIOLOGICAL` | No | Graph lookup | New |

## 14. `individual.pregnancyOutcome`

| Pedigree field | QP source | Question needed? | Transformation | Status |
|---|---|---|---|---|
| `type` | `PREGNANCY_OUTCOME.outcome_type` | Yes | Direct copy | New |
| `gestationalWeeks` | `PREGNANCY_OUTCOME.gestational_weeks` | Yes, optional | Direct copy | New |
| `lmpDate` | `PREGNANCY_OUTCOME.lmp_date` | Yes, optional | Direct copy | New |

## 15. `individual.assistedReproduction`

| Pedigree field | QP source | Question needed? | Transformation | Status |
|---|---|---|---|---|
| `type` | `ASSISTED_REPRODUCTION.reproduction_type` | Yes | Direct copy | New |
| `eggDonorId` / `spermDonorId` / `gestationalCarrierId` | `ASSISTED_REPRODUCTION_PARTICIPANT` filtered by role | No | Graph lookup | New |
| `intendedParents[]` | same, role `INTENDED_PARENT` | No | Graph lookup | New |
| `donatedTo[]` | same, role `DONATED_TO` | No | Graph lookup | New |
| `carriedFor[]` | same, role `CARRIED_FOR` | No | Graph lookup | New |
| `anonymous` | `ASSISTED_REPRODUCTION.anonymous` | Yes, optional | Direct copy | New |

## 16. `individual` — group symbol (out of scope)

| Pedigree field | QP source | Question needed? | Transformation | Status |
|---|---|---|---|---|
| `groupSymbolCode` | — | No | We never collapse individuals into one symbol — data is collected per-person | Always null |
| `groupCount` | — | No | Same | Always null |

---

## 17. API-level gaps — found 2026-10-25, not covered by v2 at all

v2 was a pure field-mapping exercise against the schema and didn't track API surface. Re-reading `QP-Family-Relationship-Persistence-Design.md` (2026-10-23) against the actual `FamilyMemberController`/`FamilyMemberService` code found that document's own decisions aren't fully built yet — these aren't pedigree-schema gaps (nothing here blocks any field above), they're gaps against that *other* design doc's own stated scope, listed here because they affect how reliably the relationship graph §11 depends on gets populated in the first place:

| Planned (per `QP-Family-Relationship-Persistence-Design.md`) | Status |
|---|---|
| `GET .../family-members/candidates` — ranked existing-person candidates for a given scope+relationshipType, structural match first then name+birth-date (§2.5 of that doc) | **Not built.** No such endpoint or matching logic exists anywhere in `FamilyMemberController`/`FamilyMemberService` today. |
| A "link existing person into a new role" path, reusing the role-creation + second-parent-attach logic (§2.12) | **Not built.** `FamilyMemberController.create()` only ever creates a brand-new `FamilyMember` row — there's no path to attach an *existing* family member (or the proband) into an additional role under a different scope. |
| Duplicate-identity prevention beyond the existing singular-type check | **Partially built.** `FamilyMemberService` still only blocks a second `MOTHER`/`FATHER` under the same scope (`SINGULAR_RELATIONSHIP_TYPES`, pre-existing FR-13 logic) — the structural/name+DOB candidate search that §2.3-2.5 of the design doc describes as the actual duplicate-prevention mechanism doesn't exist, because it depends on the `candidates` endpoint above. |
| Retroactive second-parent backfill when a `PARTNER_RELATIONSHIP` is recorded *after* children already exist (§2.11) | **Not built**, and explicitly flagged as deferred in `FamilyMemberService.autoAttachSecondParent()`'s own comment. `autoAttachSecondParent()` only fires at child-creation time today. |

None of these block anything pedigree-export-related today (a session can still be exported with whatever edges exist), but they are the gap between "the 2026-10-23 design is decided" and "the 2026-10-23 design is finished" — worth tracking separately from the pedigree-schema checklist above.

**Test-coverage gap, found 2026-10-25 while verifying this section**: `autoCreateGraphEdges()`/`autoAttachSecondParent()` (the logic that makes adding a `SON`/`DAUGHTER`/`SPOUSE` family member automatically create the matching `PARENT_CHILD_RELATIONSHIP`/`PARTNER_RELATIONSHIP` row, including the second-parent auto-attach) has **no automated test anywhere** — no test file in `QP/src/test/java` injects `ParentChildRelationshipRepository` or `PartnerRelationshipRepository`, and `PedigreeExportServiceTest` (which does exercise export over a multi-generation family) builds every one of its grandparent/sibling edges via **explicit, manual** `ParentChildRelationshipService`/`PartnerRelationshipService` calls, bypassing the auto-create path entirely — none of its fixture family members use `SON`/`DAUGHTER`/`SPOUSE`, so the auto-create branches are never hit by any test. This is not a "does it work" question — **it does work**: the real dev database (`questionnaire_platform_final`) shows a `SPOUSE` added 2026-10-23, its partner relationship manually updated to `reproductiveStatus = HAS_CHILDREN`, then a `DAUGHTER` and a `SON` added, and the resulting `parent_child_relationship` rows (one linking the proband, one linking the spouse, both carrying the exact same `created_at` timestamp as the role-creation call — the single-transaction signature `autoCreateGraphEdges`/`autoAttachSecondParent` would produce) confirm it fired correctly in that live session. But that's one manually-observed data point, not a repeatable regression test — a future change here could silently break it with nothing to catch it. Worth a dedicated `FamilyMemberServiceTest` asserting the created rows directly, not just inferring correctness from export output.

**Also found while verifying this section**: `PartnerRelationship.java`'s own class-header comment is stale — it still says "Deliberately redundant with `FAMILY_MEMBER.relationship_type = SPOUSE`," a column that was removed 2026-10-23 (§0 above). The entity's actual columns are correct and unaffected; only the comment needs a follow-up edit.

## 18. Undocumented implementation details found during this revision

Two small, real things that exist in the running code but were never written down anywhere before this revision:

1. **`age_at_death` has no dedicated column.** See the `deathYear` row in §3 — it's stored in `PERSON`/`FAMILY_MEMBER.additional_facts` (the generic JSONB catch-all), not a real column, and `PedigreeExportService` parses it back out of that blob. Works today; flagged so nobody is surprised by a JSONB lookup where they expected a getter.
2. **`external`/`donor`/`notExamined` are hardcoded in the export output**, not omitted. See the §3 rows — the *data model* question (add a column or not) is still genuinely open, but the *export* already emits `false` for all three on every individual. Worth an explicit decision (keep emitting `false`, or switch to omitting the keys) rather than leaving it as an unintentional default.

---

## Summary — what's actually blocking

**Still exactly 4 genuinely open *data-model* decisions** (unchanged from v2 — re-verified 2026-10-25, none of them have been resolved or overtaken by code), matching `QP-Pedigree-Schema-Design.md` §6:

1. **The >4-disease rule** (§1, §2, §7) — which diseases get "featured" with a quadrant when a family has more than 4. Deliberately left for your mentor.
2. **Carrier-status derivation** (§6) — the rule for deriving `carrierHet`/`asymptomaticCarrier`/`obligateCarrier` from genetic-test results doesn't exist yet, and explicitly shouldn't be designed until genetic-test data exists and is populated. Not Phase 1.
3. **`CANCER_RISK_PROFILE` scope** (§10) — whether this is required QP collection scope or a later, separate CanRisk/BOADICEA integration concern.
4. **`external`/`notExamined`** (§3, §6) — default leaning is to **not** add either field unless a concrete business need shows up — **now sharpened by §18.2**: the export already silently emits `false` for both, so this decision is overdue, not just theoretical.

**What changed since v2, net of all the above**: the relationship-graph computation (§11) and the answer-persistence plumbing for diagnoses/genetic findings (§7, §8) are genuinely done — more than v2 gave the codebase credit for. What's still undone is unchanged in kind, just smaller in scope than it looks at first glance: `DISEASE_CATALOG` + the `DISEASE`/`MUTATION` reshape (§7, §8), `GENDER_TRANSITION` (§4), and Section G (`TWINSHIP`, `ADOPTION`, `PREGNANCY_OUTCOME`, `ASSISTED_REPRODUCTION` — §12-15), plus the three API-level gaps in §17. See `QP-Pedigree-Schema-Design.md` §7 for the Phase 1A/1B implementation order — this mapping document is the *field checklist*, not the *build order*.
