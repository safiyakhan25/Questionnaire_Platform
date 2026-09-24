# Software Requirements Specification
## Questionnaire Platform — Phase 1 (Personal Details / Family History Data Collection)

| | |
|---|---|
| **Document status** | Draft for review |
| **Version** | 1.4 |
| **Date** | 2026-09-15 (last revised 2026-09-21) |
| **Prepared by** | Safiya Khan |
| **Project** | Questionnaire Platform (genetic-risk / family-history intake) |

---

## 1. Introduction

### 1.1 Purpose
This document specifies the functional and non-functional requirements of the Questionnaire Platform — a backend-driven system for collecting structured personal and family-history information for a genetic-risk assessment workflow. It is intended for the development team and mentor as the authoritative description of what the system must do, consolidating and reconciling the project's design documents into a single specification.

### 1.2 Scope
The platform collects a proband's Personal Details and Family History (and, in later phases, Medical History and Genetic Testing data) through a dynamic, rule-driven, form-based questionnaire. All business logic — workflow sequencing, question/template applicability, validation, and persistence — resides on the backend; clients are limited to rendering forms and collecting input.

Phase 1 delivers data collection only. Assessment, risk scoring, pedigree generation, and reporting are explicitly out of scope and are deferred to later phases; the data model is shaped so those capabilities can be added without restructuring.

### 1.3 Definitions, Acronyms, and Abbreviations

| Term | Meaning |
|---|---|
| Proband | The primary subject of the assessment (the patient). |
| Workflow | A top-level questionnaire section (e.g., Personal Details, Family History). |
| Template | A reusable, ordered set of questions within a workflow. A workflow can contain multiple templates. |
| Question | A single item within a template, with a type, options, validation rules, and a visibility rule. |
| Session | One assessment run for a person, tracked via `QUESTIONNAIRE_SESSION`. |
| Business entity | `PERSON` or `FAMILY_MEMBER` — the current-state record a question's answer is written onto. |
| Rule Engine | The generic JSONLogic evaluator used for both template navigation and question visibility rules. |
| answer_key | The business identifier a rule uses to read a value from the current business entity. |
| Facts | The current field values on the relevant business entity, as read by the Rule Engine during evaluation. |

### 1.4 References
This SRS is derived from, and reconciles, the following project documents (all in `Questionnaire_Platform-docs/`):

- `Questionnaire_Platform_HLD.docx` — original High-Level Design (historical; superseded where noted below)
- `QP-Post_HLD_Implementation_Context.md.txt` — Post-HLD architecture decisions (supersedes the HLD where they conflict)
- `QP-Execution_Flow.docx` — end-to-end execution flow
- `QP-APIsList.docx` — REST API specification
- `QP-DataModel.drawio` — entity-relationship diagram
- `QP-Doubts.docx` — design-approach exploration and recommendations (7 items) plus meeting-minutes decisions
- `Final Conclusions.docx` — consolidated final decisions

Where these documents previously disagreed with one another (naming, API paths, schema details), this SRS uses the resolutions confirmed on 2026-09-15 and 2026-09-16.

### 1.5 Document Conventions
Functional requirements are numbered `FR-<n>`. Each requirement is stated as a testable "the system shall" statement. Items explicitly marked **[OPEN]** represent requirements whose mechanism is not yet finalized in any source document; they are described by their known constraints only, and must not be treated as implementation-ready until resolved.

---

## 2. Overall Description

### 2.1 Product Perspective
The platform is a backend-driven, layered system. The client is a thin renderer with no business logic; all decisions about what is shown, what is valid, and what happens next are made server-side.

```
Client (Web / CLI)
   -> REST API                  (single integration surface; no business logic on the client)
      -> Workflow Engine        (orchestration: sequences workflows/templates, owns session progress)
         -> Template Engine     (loads a template and its questions)
         -> Rule Engine         (generic JSONLogic evaluator; no questionnaire-specific logic)
      -> Persistence Layer      (PostgreSQL: session, response, and business-entity state)
```

A command-line client is the first consumer, but the system is built as a platform, not a CLI application, so that Web and Mobile clients can be added later without changes to the core.

### 2.2 Product Functions (Summary)
- Start or resume a questionnaire session for a proband.
- Deliver questions as a complete, per-template form (group-by-template), not one question at a time.
- Dynamically show or hide questions based on rules evaluated against the proband's/relative's current data.
- Persist every answer immediately as it is given.
- Navigate between templates and workflows under backend control, skipping inapplicable sections.
- Allow the proband to browse and edit previously given answers without disrupting their actual progress.
- Allow administrators to configure the workflow/template/question hierarchy.
- Support repeated collection of family-member data (e.g., multiple siblings) without duplicate or conflicting records.
- Recognize a returning proband, or a family member who later becomes a proband in their own right, and reuse previously collected data rather than re-collecting it.

### 2.3 User Classes and Characteristics
- **Proband / Patient (end user):** interacts with the assessment-execution APIs only; fills in the questionnaire for themselves and their family members.
- **Administrator:** manages the question bank — creates and edits workflows, templates, and questions via the admin APIs. No further role distinctions are defined in any source document.

### 2.4 Operating Environment
- Backend: Java 21, Spring Boot 4.1.
- Database: PostgreSQL, schema managed exclusively through Liquibase (not Hibernate auto-DDL).
- Rule evaluation: JSONLogic.
- First client: command-line interface; the REST API is designed to support Web and Mobile clients without backend change.

### 2.5 Design and Implementation Constraints
- All business logic, validation, and navigation decisions must reside on the backend; no business logic is implemented client-side.
- The database schema is versioned exclusively through Liquibase changesets; an already-applied changeset must never be edited (a new changeset is used for corrections).
- Rules are configuration (stored as JSONB), not hardcoded logic, so clinical/branching logic can change without a code deployment.

### 2.6 Assumptions and Dependencies
- Phase 1 covers the Personal Details workflow first; Family History, Medical History, and Genetic Testing are built incrementally on the same architecture.
- No authentication/login system exists or is planned for the current phase (see §7.1 for the session-identification mechanism used instead).
- Patient identity matching for a returning proband across separate sessions is resolved via a lightweight identifying field, not a login system — see FR-17.

---

## 3. System Architecture

### 3.1 Layers and Responsibilities

| Component | Responsibility |
|---|---|
| REST API | Single integration surface for all clients. No business logic. |
| Workflow Engine | Orchestrates workflow/template sequencing; owns session progress; is the only component that decides "what happens next." |
| Template Engine | Loads a template and its questions. Does not decide applicability or visibility. |
| Rule Engine | Evaluates a single JSONLogic expression against whatever business-entity facts it is given, and returns TRUE/FALSE. It does not know whether the result means "show this question" or "load this template" — the caller interprets the result. |
| Persistence Layer | Stores sessions, responses, configuration, and business-entity data in PostgreSQL. |

### 3.2 Rule Evaluation Model
There are exactly two levels of rule evaluation, using the same generic Rule Engine:

| | Template Rule | Question Rule |
|---|---|---|
| Purpose | Determines whether a template should be loaded or skipped | Determines whether a question should be displayed |
| Interpreted by | Workflow Engine | Question/form handling logic |
| TRUE means | Load the template | Show the question |
| FALSE means | Skip to the next candidate template | Hide the question |

Rules are evaluated against the current state of the relevant business entity (`PERSON` or `FAMILY_MEMBER`), never directly against the `QUESTION_RESPONSE` audit log. An answer is first persisted and mapped onto the business entity; the updated business entity is then the source of truth for any subsequent rule evaluation.

A question carries exactly one rule type — `visibility_rule` (show/hide). A separate `disable_rule` (keep the question visible but non-interactive) existed in an earlier revision of this design but was removed: FR-11 guarantees a rule can only reference an *earlier* template's answer_keys, never its own template's, so no question's state can ever change as a result of another answer given within the same template. A disable-style "interactive vs. locked" rule would only ever matter within the currently-rendered template — but that is exactly the case FR-11 rules out. See FR-10.

---

## 4. Data Requirements

### 4.1 Entities

| Table | Purpose |
|---|---|
| `WORKFLOW` | A questionnaire section (e.g., Personal Details), ordered by `display_order`. |
| `TEMPLATE` | A reusable question set inside a workflow. Ordered by `display_order`; the template with the lowest `display_order` in a workflow is its default/entry template and is loaded unconditionally. Carries `template_rule` (JSONB) controlling navigation. |
| `QUESTION` | One question, belonging to a template. Carries `options` and `validation` (JSONB) inline, plus `visibility_rule` (JSONB, nullable) — see FR-10. |
| `PERSON` | The proband. Carries `additional_facts` (JSONB, nullable) as a flexible bucket for collected facts that don't map onto a fixed column. |
| `FAMILY_MEMBER` | A relative, person-shaped, linked to the proband via `proband_id`. Also carries `additional_facts`, same purpose as on `PERSON`. |
| `QUESTIONNAIRE_SESSION` | One assessment run for a person. `status` is `IN_PROGRESS` or `COMPLETED`. Carries `previous_template_id` and `previous_workflow_id` for resume/navigation handling (see §6.1, §6.8). |
| `QUESTION_RESPONSE` | Append-only log of every answer. Carries `answer_key` and `template_id` directly (avoiding a join through `QUESTION` when querying a session's responses for one template). `relationship_instance_id` disambiguates repeated family-member instances. `answer_type` is an enum (`BOOLEAN`, `NUMBER`, `STRING`, `ARRAY`, `OBJECT`). |
| `DISEASE` | A diagnosis, belonging to exactly one of `PERSON` or `FAMILY_MEMBER` (enforced by a check constraint). |
| `MUTATION` | A genetic finding, with the same single-owner constraint as `DISEASE`. |

### 4.2 Enumerations

| Enum | Values |
|---|---|
| `QuestionType` | `TEXT`, `NUMBER`, `DATE`, `RADIO`, `CHECKBOX`, `DROPDOWN` |
| `SessionStatus` | `IN_PROGRESS`, `COMPLETED` |
| `AnswerType` | `BOOLEAN`, `NUMBER`, `STRING`, `ARRAY`, `OBJECT` |
| `GenderAtBirthType` | `MALE`, `FEMALE`, `INTERSEX`, `UNKNOWN` |
| `MaternalPaternalSideType` | `MATERNAL`, `PATERNAL`, `UNKNOWN` |
| `RelationshipType` | `MOTHER`, `FATHER`, `BROTHER`, `SISTER`, `SON`, `DAUGHTER`, `SPOUSE`, `HALF_BROTHER`, `HALF_SISTER`, `ADOPTED_SON`, `ADOPTED_DAUGHTER`, `GRANDFATHER`, `GRANDMOTHER`, `UNCLE`, `AUNT`, `COUSIN`, `NIECE`, `NEPHEW` |

### 4.3 Data Integrity Rules
- `DISEASE` and `MUTATION`: exactly one of `person_id` / `family_member_id` must be set (XOR check constraint).
- Stored answer values use the underlying value defined by the question's `options`, never the display label (e.g., a Yes/No question persists `true`/`false`, not the strings "Yes"/"No").
- `QUESTION_RESPONSE` is append-only; an edited answer is represented as a new response record, not an in-place update, so history is never lost.

---

## 5. External Interface Requirements — REST API

### 5.1 Admin/Configuration APIs (Workflow → Template → Question)

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/v1/admin/workflows` | Create a new workflow section. |
| GET | `/api/v1/admin/workflows` | List all workflows, ordered by `displayOrder`. |
| GET | `/api/v1/admin/workflow/{id}` | Get one workflow. |
| PUT | `/api/v1/admin/workflow/{id}` | Update name, description, or `display_order`. |
| DELETE | `/api/v1/admin/workflow/{id}` | Delete one workflow. |
| POST | `/api/v1/admin/workflow/{workflowId}/templates` | Create a template under a workflow. |
| GET | `/api/v1/admin/workflow/{workflowId}/templates` | List the templates belonging to one workflow, ordered by `displayOrder`. |
| GET | `/api/v1/admin/template/{templateId}` | Get one template. |
| PUT | `/api/v1/admin/template/{templateId}` | Update a template, including `templateRule` and `displayOrder`. |
| DELETE | `/api/v1/admin/template/{templateId}` | Delete a template. |
| POST | `/api/v1/admin/template/{templateId}/questions` | Create a question. Body includes `answerKey`, `text`, `type`, `displayOrder`, plus nested `options[]`, `validation{}`, `visibilityRule{}` as JSON. |
| GET | `/api/v1/admin/template/{templateId}/questions` | List the questions belonging to one template, ordered by `displayOrder`. |
| GET | `/api/v1/admin/question/{questionId}` | Get one question, including the full JSONB payload. |
| PUT | `/api/v1/admin/question/{questionId}` | Update text/type/options/validation/visibilityRule. |
| DELETE | `/api/v1/admin/question/{questionId}` | Delete a question. |

Naming convention: resource names are plural for collection-level operations (create/list) and singular where a specific ID is being addressed. Global (non-scoped) listing of all templates or all questions is intentionally not provided; both are always retrieved scoped to their parent.

### 5.2 Assessment Execution APIs (Patient-Facing)

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/v1/assessment/start` | Start a new session, or resume the existing `IN_PROGRESS` one for that person. Creates the session and a new `PERSON` on a fresh start; reuses the existing `person_id` on resume. Returns the current template's full question set, each one flagged visible or not-yet-visible, pre-filled with any answers on record. |
| GET | `/api/v1/assessment/{sessionId}/current-template` | Idempotent fetch of the current template, resolved via `previous_template_id` — same shape as `POST /api/v1/assessment/start`. Used for page refreshes or reopening a session; reads only, changes nothing. |
| POST | `/api/v1/assessment/{sessionId}/answer` | Submits one answer. Persists it as a `question_response` (stored value, not the display label) and updates the matching `PERSON`/`FAMILY_MEMBER` field. Triggers no re-evaluation (see FR-4) — nothing in the currently-rendered template can depend on it. Template completion is handled separately, by `next-template`. |
| POST | `/api/v1/assessment/{sessionId}/next-template` | Explicit action (not auto-advance). Marks the current template complete, evaluates the next candidate template's rule (across workflows too), and returns the next template or a "session completed" signal. |
| GET | `/api/v1/assessment/{sessionId}` | Status only: in-progress/completed, started/finished timestamps. |
| GET | `/api/v1/assessment/{sessionId}/responses` | Full readback of every answer given so far in the session. |
| POST | `/api/v1/assessment/{sessionId}/family-members` | Starts a fresh pass through the Family Member questions for another relative. |
| DELETE | `/api/v1/assessment/{sessionId}/family-member/{familyMemberId}` | Deletes a family member the patient added by mistake, before the questionnaire is completed. |

---

## 6. Functional Requirements

### FR-1 — Session Start and Resume
The system shall, on `POST /api/v1/assessment/start`:
1. Create a new `PERSON` record and a new `QUESTIONNAIRE_SESSION` record (status `IN_PROGRESS`) if no `IN_PROGRESS` session exists for the requester.
2. Reuse the existing `IN_PROGRESS` session and its existing `person_id` if one already exists, without creating a duplicate `PERSON`.
3. Return the resolved current template's full question set (visible and not-yet-visible questions both included, each flagged), pre-filled with any existing answers.

### FR-2 — Question Visibility Evaluation
The system shall evaluate every question in the loaded template using the same mechanism regardless of when the evaluation occurs (first load, resume, or after an answer): a question with no `visibility_rule` is unconditionally shown; a question with a `visibility_rule` is evaluated against current business-entity facts. There is no special-cased behavior for "first load" — a question appearing hidden on a fresh session is a consequence of its dependency not yet existing, not of evaluation being skipped.

### FR-3 — Answer Persistence
The system shall, on `POST /api/v1/assessment/{sessionId}/answer`:
1. Persist the submitted answer as a new `QUESTION_RESPONSE` record, using the underlying stored value (not the display label).
2. Update the corresponding field on the relevant business entity — `PERSON` or `FAMILY_MEMBER` for demographic/personal data; a new `DISEASE` or `MUTATION` record for a diagnosis or genetic finding, attached to whichever subject (proband or relative) it describes.
3. Not decide or perform template completion or navigation as part of this action.

### FR-4 — No Re-evaluation Within a Template
The system shall not re-evaluate any question's `visibility_rule` after an answer is submitted within the same template. This is a direct consequence of FR-11: a rule may only reference an `answer_key` from an *earlier* template, never its own, so no question in the currently-rendered template can ever be affected by an answer given elsewhere in that same template. Re-evaluation only ever happens when a *later* template is loaded (FR-2, evaluated against the facts collected so far) or when that later template's own `template_rule` is checked (FR-5) — never mid-template.

*Superseded 2026-09-21: this requirement previously specified a targeted re-evaluation mechanism (re-check only the dependent question(s) after each answer). That mechanism is now known to be unreachable in practice — given FR-11, the set of "currently-rendered questions that depend on the just-changed answer_key" is always empty — so it was removed rather than kept as dead code. This also removed the need for a second question-level rule type (see FR-10 — `disable_rule` was removed for the same reason).*

### FR-5 — Template Navigation
The system shall, on `POST /api/v1/assessment/{sessionId}/next-template`:
1. Evaluate the next candidate template's `template_rule` against current business-entity facts.
2. Load the template if the rule evaluates TRUE.
3. Skip to the next candidate template, by `display_order`, if the rule evaluates FALSE, continuing into the next workflow if the current workflow's templates are exhausted.
4. Return a "session completed" signal, and set the session `status` to `COMPLETED`, when no further applicable template exists.

### FR-6 — Required vs. Optional Questions
The system shall block template completion only on required questions; optional questions may remain unanswered indefinitely without blocking navigation.

### FR-7 — Session Status and History Retrieval
The system shall provide a lightweight session-status endpoint (`GET /api/v1/assessment/{sessionId}`) returning only `status` and timestamps, and a full-transcript endpoint (`GET /api/v1/assessment/{sessionId}/responses`) returning every answer recorded for the session.

### FR-8 — Previous/Forward Navigation
The system shall allow a proband to browse templates they have actually visited, without altering their real session progress:
1. "Previous" shall be computed from the proband's actual answered path (templates grouped by `template_id` and `relationship_instance_id`, ordered by first-answered timestamp) — never recalculated from current rules, and never simply the prior template in display order, since applicability varies per proband.
2. Templates that were skipped or never visited shall not appear as a "previous" step.
3. The system shall distinguish between the proband's real position (unaffected by browsing) and a separate, temporary viewing position that moves as Previous/Forward are used.
4. A "return to where I left off" action shall restore the real position directly, regardless of how many steps back the proband had browsed.
5. Browsing via Previous/Forward shall not, by itself, submit or modify any answer.
6. A separate "go back to here" action, distinct from ordinary Previous/Forward browsing, shall move the real session position to the template currently being viewed, so that an edit can be submitted against it. This is the only path by which editing a previous answer (FR-9) becomes possible — there is no inline editing while merely browsing in read-only mode.

### FR-9 — Editing Previous Answers
The system shall allow previously given answers to be edited. Editing is only reachable via an explicit "go back to here" action (see FR-8.6); a proband cannot submit an edit merely by browsing a previous template in read-only viewing mode — invoking "go back to here" moves the real session position to that template, after which editing proceeds through the normal answer-submission path (FR-3), including the cascade below.

When an answer in an earlier template is changed:
1. The system shall identify downstream questions whose rules reference the changed `answer_key`.
2. Affected questions shall be re-evaluated. Where re-evaluation invalidates a prior answer, "nullifying" it means resetting the corresponding field on `PERSON`/`FAMILY_MEMBER` to unset — the historical `QUESTION_RESPONSE` record for that answer is left untouched, consistent with the append-only rule in §4.3. The business entity, not `QUESTION_RESPONSE`, is what must reflect "currently unanswered" for subsequent rule evaluation.
3. If a downstream template becomes entirely inapplicable as a result, the business-entity fields for all answers belonging to that template shall be nullified in the same way.

### FR-10 — Question Rule Type: Show/Hide
The system shall support exactly one rule type per question, evaluated by the Rule Engine: **Show/Hide** (`visibility_rule`), which determines whether the question exists in the rendered form at all.

*Superseded 2026-09-21: this requirement previously also specified a second rule type, Disable (`disable_rule`) — keeping a question, or a specific option within it, visible but not interactive. Removed once FR-11 made it unreachable: a disable-style "interactive vs. locked" state would only ever change within the currently-rendered template, and FR-11 guarantees nothing in that template can depend on another answer given in that same template. If a genuine need for disqualifying a specific option, or locking a superseded answer as read-only, comes up later, it should be reconsidered as a fresh requirement against the current design rather than reinstating this one as-is.*

### FR-11 — Template Dependency Restriction
A rule (a question's `visibility_rule`, or a template's `template_rule`) may reference only `answer_key`s belonging to questions in an earlier template — any template before it, not just the one immediately before — by configured `display_order`. It may never reference an `answer_key` belonging to its own template. "Earlier" refers to the statically configured template ordering, not any individual proband's runtime path, since skipped templates make the runtime path vary per proband.

This restriction is enforced by convention, not by automated runtime validation: whoever authors a question or template rule is responsible for ensuring it only references answer_keys already collected in an earlier template, never its own. No save-time check, admin-API validation, or database constraint enforces this — it is a documented authoring rule for question-bank maintainers to follow.

### FR-12 — Family Member Management
The system shall allow the proband to add family members one at a time (`POST /api/v1/assessment/{sessionId}/family-members`) and remove a mistakenly added one before the session completes (`DELETE .../family-member/{familyMemberId}`).

### FR-13 — Duplicate Family Member Prevention
1. For relationship types that are singular per proband (`MOTHER`, `FATHER`), the system shall permit at most one `FAMILY_MEMBER` record per `(proband_id, relationship_type)`.
2. For `GRANDMOTHER`/`GRANDFATHER`, the system shall permit at most one record per `(proband_id, relationship_type, maternal_paternal_side)`.
3. For relationship types that are legitimately plural (siblings, children, aunts/uncles, cousins, nieces, nephews), the system shall reconcile the number of recorded family members against a declared count rather than attempting duplicate detection by relationship type alone.

### FR-14 — Family Member / Existing Person Data Reuse
When a family member who has already been recorded elsewhere in the platform later completes their own questionnaire as a proband, the system shall recognize the existing record and reuse previously collected data rather than re-collecting it, via a deterministic reference token:
1. When a family member is added within a session, the system shall be able to generate a shareable reference token associated with that `FAMILY_MEMBER` record.
2. When that individual later starts their own session (`POST /api/v1/assessment/start`) presenting a valid reference token, the system shall link the resulting `PERSON` record to the existing `FAMILY_MEMBER` record it was generated from, and reuse the data already collected for it, rather than asking the questions again.
3. Matching shall be deterministic (token-based) only. Automatic matching by similarity of name, date of birth, or other identifying fields shall not be performed, given the risk of incorrectly linking two different individuals' health records in a family context where names and ages commonly overlap. A person or administrator may still link records manually when no token was used.

### FR-15 — Answer Validation
The system shall validate every submitted answer on the backend (via a dedicated validator invoked from the answer endpoint) before it is persisted or used to update business-entity data. The system shall not rely on any client-side validation for correctness; client-side validation, if present, is for UX purposes only.

The `validation` JSONB field shall use a fixed, closed set of named keys, recognized directly by the validator, rather than an embedded JSONLogic expression:

| Key | Applies to | Meaning |
|---|---|---|
| `required` | Any type | Answer must be present before the template can be completed. |
| `minLength` / `maxLength` | `TEXT` | Minimum/maximum character length. |
| `min` / `max` | `NUMBER`, `DATE` | Minimum/maximum numeric value or date. |
| `pattern` | `TEXT` | A regular expression the value must match. |

This set may be extended with additional named keys as new question types or validation needs arise; a JSONLogic-based validation escape hatch is deliberately not included unless a concrete need for cross-field or compound validation logic actually arises.

### FR-16 — Session Identification
The system shall identify the active session via a signed cookie carrying the `sessionId`. `personId` shall never appear in a URL. The `sessionId` shall not be unnecessarily exposed in browser history or application logs.

### FR-17 — Returning Patient Identification
When starting a new session, the system shall collect a lightweight identifying field (e.g., email or phone number) from the proband. If the value matches an existing `PERSON` record, the system shall prompt the proband to confirm before linking the new session to that existing record; it shall not link automatically without confirmation. If no match is found, or the proband declines the link, a new `PERSON` record is created as in FR-1. This mechanism is distinct from FR-14: FR-14 links a new proband to a record originally created as someone else's `FAMILY_MEMBER`; FR-17 links a returning proband's new session to their own prior `PERSON` record.

---

## 7. Non-Functional Requirements

### 7.1 Security
- All validation and business-logic decisions are enforced server-side; the client is not trusted for correctness.
- Session identity is carried in a signed cookie, not a URL parameter, to avoid leaking session identifiers through logs, browser history, or referrer headers.
- `personId` is never exposed to the client in a URL.
- A full login/JWT-based authentication system is explicitly out of scope for the current phase.

### 7.2 Performance
- `template_id` is stored directly on `QUESTION_RESPONSE` specifically to avoid a join through `QUESTION` when retrieving a session's responses for a given template.
- Submitting an answer triggers no rule re-evaluation at all (FR-4) — not "a bounded amount," none — since FR-11 guarantees nothing in the current template can depend on it. The cost of `POST /answer` is a single insert plus a business-entity update, regardless of template size.

### 7.3 Maintainability
- Rules (both template-level and question-level) are stored as JSONB configuration, evaluated by a single generic JSONLogic engine, so clinical/branching logic can change without a code deployment.
- The Workflow, Template, and Rule engines are single-responsibility components: a change to stage ordering, question content, or conditional behavior touches exactly one engine.

### 7.4 Data Integrity
- `QUESTION_RESPONSE` is append-only; no answer is ever overwritten in place, preserving a complete audit trail and enabling FR-8/FR-9.
- `DISEASE` and `MUTATION` ownership is enforced by a database check constraint (exactly one of `person_id`/`family_member_id`), not application logic alone.

### 7.5 Database and Schema Management
- PostgreSQL is the database. The schema is created and versioned exclusively through Liquibase changesets. Hibernate DDL auto-generation is not used to create or alter the schema.

---

## 8. Constraints and Assumptions Carried from Design Discussion

### 8.1 Interaction Model
The platform is form-based, not chat-based or CLI-style one-question-at-a-time. When a template is loaded, the backend returns every question belonging to that template in one response, and the client renders them as a single form.

### 8.2 Workflow/Template Structure
A workflow can contain multiple templates. Each workflow has exactly one default template (the one with the lowest `display_order`), loaded unconditionally, without its `template_rule` being evaluated.

---

## 9. Current Implementation Status

As of this revision (2026-09-21), the following is true of the codebase (distinct from the design decisions described above, which represent the target state):

| Area | Status |
|---|---|
| Database schema (Liquibase migrations) | Implemented — `workflow`, `template`, `question`, `person`, `family_member`, `questionnaire_session`, `question_response`, `disease`, `mutation` tables exist, fully matching §4.1. `question.disable_rule` was dropped (changeset `013`) — see FR-10. |
| `previous_template_id` / `previous_workflow_id` on `questionnaire_session` | Implemented. |
| `template_id` on `question_response` | Implemented. |
| `visibility_rule` on `question` | Implemented (single rule type — `disable_rule` removed, see FR-10). |
| `additional_facts` on `person` / `family_member` | Implemented. |
| `display_order` uniqueness (per parent: workflow-wide, per-workflow for template, per-template for question) | Implemented as a DB unique constraint plus a service-level check. |
| JPA entities and repositories | Implemented for all 9 tables, fully matching the current schema. |
| Rule Engine | Implemented (`engine/RuleEngine`) — wraps `io.github.jamsesso:json-logic-java`, same library/contract as the `rule-engine-poc` proof of concept. |
| Workflow Engine, Template Engine | Not implemented as separate components — their responsibilities (template resolution, navigation) currently live inside `AssessmentService`, which is small enough at this scope not to need the split yet. |
| Admin/Configuration APIs (§5.1) | Fully implemented and live-tested — all 15 Workflow/Template/Question CRUD endpoints. |
| Assessment Execution APIs (§5.2) | Core execution loop implemented and live-tested: `start` (create-or-resume-by-sessionId), `current-template`, `answer`, `next-template`, session `status`, `responses`. **Not yet implemented**: family-member management (FR-12/13/14), previous/forward browsing and the edit-and-nullify cascade (FR-8/9), signed-cookie session identification (FR-16 — `sessionId` is currently a plain field/path parameter), and returning-patient matching by identifying field (FR-17). |
| Seed data | No Liquibase seed migration. Workflow/Template/Question configuration is seeded by calling the admin APIs (§5.1). |

---

## 10. Traceability to Source Documents

Every functional requirement in §6 is derived directly from a specific source document, per §1.4:

- FR-1, FR-2, FR-3, FR-5, FR-6: `QP-Execution_Flow.docx`, `QP-Post_HLD_Implementation_Context.md.txt`
- FR-4: `QP-Post_HLD_Implementation_Context.md.txt` §6, `QP-Execution_Flow.docx` step 4
- FR-7, FR-12, FR-13: `QP-APIsList.docx`, `QP-Doubts.docx` §7
- FR-8, FR-9: `QP-Doubts.docx` §4 (Scenarios 1–4)
- FR-10: `QP-Doubts.docx` §6, `Final Conclusions.docx`
- FR-11: `QP-Doubts.docx` MOM section ("Template Dependency")
- FR-14: `Final Conclusions.docx` ("Family Member / Existing Person Data Reuse")
- FR-15: `QP-Doubts.docx` §1, `Final Conclusions.docx`; validation key structure resolved 2026-09-16
- FR-16: `QP-Doubts.docx` §3, `Final Conclusions.docx`
- FR-17: identified as a gap during SRS preparation 2026-09-15; resolved 2026-09-16

---

## 11. Open Issues (Require Further Decision Before Implementation)

No open issues remain as of this revision (2026-09-16). The six items originally listed here — template dependency validation mechanism, family member/existing person matching, returning-patient identity matching, `validation` JSONB structure, the meaning of "nullify" under append-only responses, and the viewing-to-editing submission path — were all resolved on 2026-09-16 and are now reflected in FR-9, FR-11, FR-14, FR-15, and FR-17.

Future gaps identified during implementation should be added to this section following the same pattern: stated as an open constraint, never answered by assumption.
