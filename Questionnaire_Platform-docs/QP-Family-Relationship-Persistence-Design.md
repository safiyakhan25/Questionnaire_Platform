# Family Relationship Persistence — Recursive Adding & Scope-Based Roles

| | |
|---|---|
| **Status** | Decided 2026-10-23, implementation in progress same day. |
| **Origin** | Your mentor proposed adding a `scope_id` to `FAMILY_MEMBER` to record whose immediate relative each person is, after a review surfaced that nothing in the platform actually calls `relationships/parent-child`/`relationships/partners` automatically — every relationship edge has only ever been created by hand-written test scripts or manual Postman calls. |
| **Purpose** | This document is the record of every decision made while evaluating and refining that proposal, and the resulting data-model/API changes. Read this before touching family-member or relationship code again. |

---

## 1. The problem this solves

Two separate gaps, found by walking the mentor's proposal through to its conclusions:

1. **Nothing wires patient answers to relationship creation.** The question set's "Drives" column (`QP-Pedigree-Demographic-Questions.md`) documents which answer *should* trigger which relationship call, but that mapping only ever existed as prose for a human developer to read — never as code, never as an API contract a client could discover.
2. **The original `scope_id` proposal (a single column on `FAMILY_MEMBER`) can't represent a real pedigree once you allow it to solve problem 1 properly.** Two structural issues, found by tracing concrete scenarios (see §2):
   - A single scope pointer can't record **two parents** for one individual, which breaks full/half-sibling derivation and `pedigree_schema.json`'s own 2-parent model.
   - Once **recursive adding** is allowed (any family member can add their own relatives, not just the proband), the same real person can be reached through two different paths — e.g. "father's spouse" and "proband's mother" may be the same woman — and a single `relationship_type` column on that person's row can't hold both true labels at once.

## 2. Decisions made, in order

1. **Recursive adding is explicitly approved.** Any family member — not only the proband — can be the anchor ("scope") for adding further relatives. This reopens a deliberate earlier decision ("every relationship edge gets established from the proband's side, once," `QP-Pedigree-Demographic-Questions.md`) — the reopening is intentional, not accidental.
2. **Duplicate identities are prevented at creation time, never merged after the fact.** A reactive "detect two records are the same person and merge them" approach was rejected: merging requires reassigning every `QUESTION_RESPONSE`, every relationship row, and resolving conflicting data between the two records — real implementation risk, and in a genetic-counseling tool, merging two records that turn out to be *different* people would silently combine their disease/genetic history. Prevention (search before create) carries none of that risk.
3. **The search-then-create-or-link flow is a single flow, not a client choice.** The proband is never asked to decide "new or existing" themselves (they may not know if father already added this person) — the system searches automatically and surfaces a candidate if one is found.
4. **Linking is never fully automatic/silent.** Even a high-confidence match is surfaced as a one-tap confirmation ("Is this your mother? Linda Carter — already added as your father's spouse — Yes / No"), never linked without that confirmation. A wrong silent link carries the same misattributed-identity risk as a wrong merge, just moved earlier — cheaper to undo (delete one relationship edge) but not risk-free, so it still needs to be visible and explicit.
5. **Search ranks structural match above name match.** The strongest signal is relational, not textual: does an adjacent scope already have someone filling the matching role (father already has a declared `SPOUSE` when the proband declares a `MOTHER`)? This needs no fuzzy matching and can fire before any name is even typed. Exact normalized name + birth date together is the fallback for cases with no obvious structural slot (e.g. a cousin reachable from two different aunt/uncle branches). Fuzzy/approximate name matching was explicitly rejected as the primary mechanism — at the scale of one family's pedigree, it adds false-positive/negative risk without a corresponding benefit over the structural signal plus exact name+DOB.
6. **Relationship roles move off `FAMILY_MEMBER` entirely, into a new table.** Once the same person can hold more than one true relational label (`SPOUSE` relative to one scope, `MOTHER` relative to another), a single `relationship_type` column can't hold both. Considered a JSONB array on the row instead — rejected in favor of a proper join table (`family_member_role`), consistent with how this project already solves "one person, multiple related facts" everywhere else (`PARENT_CHILD_RELATIONSHIP`, `PARTNER_RELATIONSHIP`) rather than introducing a JSON-blob exception that loses DB-level type safety and breaks every existing plain-column query (FR-13's duplicate check, the reconciliation groups).
7. **`family_member_role` rows are append-only; corrections are explicit deletes, never silent overwrites.** Matches the pattern already used for every other correction in this platform (FR-9's edit cascade nullifies, it never silently overwrites history).
8. **The original `FAMILY_MEMBER.relationship_type` column is removed entirely, not kept alongside the new table.** Even the very first role a person is given lives in `family_member_role`. Keeping both would reintroduce the exact two-sources-of-truth problem this project has deliberately avoided everywhere else (why siblings aren't stored directly, why `matingLineType`/`consanguineous` were carefully decoupled rather than left to conflict).
9. **`maternal_paternal_side` stays a separate, single, proband-relative column**, untouched by the above — it answers "which branch, relative to the proband specifically," a question that isn't meaningful per-scope the way `relationship_type` now is.
10. **FR-13 duplicate prevention (at most one `MOTHER`/`FATHER`) is re-scoped per anchor, not global to the proband.** Enforced as a uniqueness constraint on `(scope_id, relationship_type)` in the new table.
11. **The second-parent gap is closed by auto-attaching from an existing partner, not by requiring the client to specify both parents every time.** When a child is added under a scope individual who has *exactly one* recorded partner, that partner is automatically attached as the second parent — this is what lets "add a sibling under mother's scope" produce a genuine full sibling without the client re-stating father.
    - **Gated on `reproductiveStatus`**: only auto-attached when that partner relationship's `reproductiveStatus` is `HAS_CHILDREN` (or equivalent) — otherwise a step-relationship could be wrongly guessed into biological co-parentage.
    - **Re-evaluated reactively**, not just at child-creation time: if a `PartnerRelationship` is recorded *after* children already exist (the actual order in most real walkthroughs so far), the second-parent attachment runs retroactively and backfills them.
12. **`PARENT_CHILD_RELATIONSHIP`/`PARTNER_RELATIONSHIP` remain as storage for edge-level facts** (consanguinity, marital status, reproductive status, ended year) — nothing about the above removes the need for a home for facts that describe a *pair*, not an individual. What changes is the API surface: creating/updating them is no longer exposed as an independent, separately-documented top-level flow for normal use — it's folded into the link/create-family-member flow. Two capabilities must survive that folding:
    - Linking **two already-existing people**, with no new person being added at all (the cross-branch consanguineous-marriage case — two cousins, each reachable via a different branch, who turn out to be partners).
    - Being **callable again later to update** edge facts (a recorded marriage later needs `relationshipStatus: DIVORCED` set) — not just set once at creation.

## 3. Database changes

### New table: `family_member_role`

| Column | Type | Notes |
|---|---|---|
| `id` | UUID, PK | |
| `family_member_id` | UUID, FK → `family_member(id)`, NOT NULL | The person holding this role. |
| `scope_person_id` / `scope_family_member_id` | UUID, FK → `person`/`family_member`, XOR | The anchor this role is relative to — same XOR idiom already used for `parent_child_relationship`/`partner_relationship`. |
| `relationship_type` | `relationship_type` enum, NOT NULL | Same enum as today — MOTHER/FATHER/SON/DAUGHTER/SPOUSE/etc. |
| `created_at` | TIMESTAMP, NOT NULL | |

Constraints: unique on `(scope_person_id, scope_family_member_id, relationship_type)` for singular types (`MOTHER`, `FATHER`) — enforced at the service layer (same pattern as today's `SINGULAR_RELATIONSHIP_TYPES` check), mirroring how `GRANDMOTHER`/`GRANDFATHER` are already scoped by `maternal_paternal_side` today.

### `family_member` table

- **Drop** `relationship_type` column (and the implicit dependency on it everywhere it was read as "relative to the proband").
- **Keep** `maternal_paternal_side` as-is — still a single column, still proband-relative, unaffected by the rest of this change.
- No new column added directly to `family_member` for "scope" — scope is now an attribute of each role row in `family_member_role`, not of the person.

### No changes to `parent_child_relationship` / `partner_relationship`

Both tables keep their current shape — they remain the storage for structural edges and edge-level facts respectively. Only their API exposure changes (see §2.12).

## 4. API changes

- `POST /api/v1/assessment/{sessionId}/family-members` gains a `scopeId` field (defaults to the proband when omitted) alongside the existing `relationshipType`/`maternalPaternalSide` — the role this creates is written to `family_member_role`, not a column.
- New: `GET /api/v1/assessment/{sessionId}/family-members/candidates` — given an intended `scopeId` + `relationshipType`, returns ranked existing-person candidates (structural match first, then name+birth-date match) for the search-before-create flow.
- New: a "link" path — attaching an *existing* family member (or the proband) into a new role under a different scope, instead of creating a new row. Reuses the same underlying role-creation + second-parent-attach logic as a fresh create.
- `FamilyMemberResponse` changes shape: `relationshipType` as a single flat field is replaced by a list of `{scopeId, relationshipType}` roles, since one person can now legitimately hold more than one.
- `PedigreeExportService` and `FamilyMemberService`'s duplicate-prevention/reconciliation logic are rewritten to read roles from `family_member_role` instead of a plain column, correctly merging a person who holds multiple roles into one exported individual.

## 5. Explicitly out of scope for this pass

- Whether recursive adding implies **separate per-person respondent sessions** (a family member literally logging in and completing their own intake) was raised and set aside — this design assumes a single proband-driven session that can *navigate into* another family member's scope to add relatives on their behalf, not a multi-respondent system. That would overlap with the already-deferred FR-14 (reference-token linking) and is a separate, larger decision.
- The `matingLineCode` export-time derivation rule remains provisional, pending your mentor's separate answer (unrelated to this change).
