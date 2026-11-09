# Patient Portal — Phase 1 Design

## Intent and why

QuestionnairePlatform (QP) has a fully-built, tested Spring Boot backend — admin CRUD for Workflow/Template/Question, patient-facing assessment execution, family-member management with recursive scope-based roles, relationship persistence, and pedigree JSON export — but no frontend and no authentication at all. This phase builds the first real, usable interface: a patient-facing web portal a real patient can use alone at home, unsupervised, to complete their genetic/family-history questionnaire.

The defining requirement, stated first and confirmed explicitly: **the frontend must be generic**, specifically meaning — if someone adds a new question to a template through the existing admin API, the patient's screen shows it automatically, with no frontend code change and no redeploy. This is achievable because the backend was already built schema-first: every question carries its own `type`/`options`/`validation`/`visibilityRule`, and nothing about its content is hardcoded anywhere in the backend.

## Goals

1. A patient can create an account (email/phone + password), log in, and have their assessment session found or started automatically.
2. The questionnaire UI is fully schema-driven off the existing admin-configured Workflow/Template/Question data — new questions appear with zero frontend changes.
3. Every current `QuestionType` renders correctly: `TEXT`, `NUMBER`, `DATE`, `RADIO`, `CHECKBOX`, `DROPDOWN` — with required-field validation, conditional visibility, and forward/backward template navigation (including resuming an earlier template to edit an answer).
4. A patient can add a family member and, while still logged in as themselves, switch into that family member's own pass and fill in their data — reusing the backend's existing subject-switching capability (`addFamilyMemberAndStartPass`/pause-resume). No separate account or shared link for the relative yet.
5. At the end of the assessment, the flow is proven to produce a valid pedigree export (the app calls `GET /pedigree-export` and confirms success; it does not need to render it for the patient).
6. Fully responsive — usable on both desktop and mobile.

## Non-goals (deferred to a later phase, not failures of this one)

- Shareable family-member access links (token generation, admin-configurable expiry, revoke, true-wipe-and-reissue) — real backend work, not yet designed.
- Admin portal (CRUD UI for workflows/templates/questions).
- Doctor/clinician portal and patient-granted doctor access.
- Phone number verification (OTP/SMS) — phone is stored as a profile field only.
- Email verification and password reset flows.

## Anti-goals (would make this a failure even if it technically runs)

- A patient getting stuck because a validation error or a required field isn't communicated clearly — no one is present to explain it to them.
- A frontend that hardcodes any specific template name, question text, or `answerKey` anywhere. That would defeat the one requirement that matters most here.
- Losing a patient's in-progress answers on a refresh or network blip — the session must always be resumable, matching the backend's own resume-by-`sessionId` support.

## Constraints

- **2-day timeline.**
- **React** (confirmed choice), built with Vite.
- The existing QP backend REST API (`/api/v1/assessment/**`) is used **unmodified** — Phase 1 adds a new, separate auth module alongside it rather than retrofitting auth onto the existing controllers.
- No new runtime infrastructure (no Redis, no external auth provider) — everything lives in the existing Postgres database via a new Liquibase changeset.

## Decided: the authentication approach

The backend has zero authentication today — nothing to extend, so this is new, deliberately minimal:

- A new `APP_USER` entity (`id`, `email` unique, `phone` nullable, `password_hash`, `session_id` nullable, `created_at`). One user maps to at most one `QuestionnaireSession`, referenced by its `sessionId` — this is the *only* link between the new auth module and the existing assessment system. No existing table or entity is modified.
- Passwords hashed with BCrypt. This requires adding `spring-security-crypto` as a new dependency — deliberately *not* `spring-boot-starter-security`, which would pull in a full filter chain, default login page, and CSRF handling that isn't needed and would cost time under this deadline.
- A new `AUTH_TOKEN` table (`token`, `app_user_id`, `created_at`) — a plain opaque random token, not a JWT. Consistent with how the rest of this backend already works (`sessionId` is itself just a plain UUID, no signed-token infrastructure exists anywhere in this project yet — see FR-16, explicitly deferred). Simpler to implement correctly in the time available than introducing JWT signing/verification.
- New endpoints, additive only: `POST /api/v1/auth/signup`, `POST /api/v1/auth/login`, `GET /api/v1/auth/me` (returns the user's profile and `sessionId`, or `null` if they haven't started one yet), `POST /api/v1/auth/me/session` (records the `sessionId` the frontend got back from its first `POST /assessment/start` call).
- The existing assessment/family-member/relationship endpoints are **not** protected by this auth layer in Phase 1 — they continue to work exactly as they do today, keyed by `sessionId` alone. The frontend is simply taught which `sessionId` belongs to the logged-in user.

## Decided: the family-member-switch flow (no sharing yet)

The proband, still logged into their own account, adds a relative via the existing `POST /family-members` (now with the real `scopeId` field this backend recently gained — defaulting to themselves for Phase 1, since recursive multi-level scoping isn't exposed in the UI yet). The response's `familyMemberId` puts the session into that relative's pass (`familyMemberId` becomes non-null in `CurrentTemplateResponse`); the same question-rendering UI is reused, tagging each answer with that `familyMemberId`. When their pass completes, the backend automatically returns control to the proband (`familyMemberId` back to `null`) — this is existing, already-tested backend behavior, nothing new to build for it.

## Decided: what's hardcoded vs. generic

`RelationshipType` and `MaternalPaternalSideType` (the enums used when adding a family member) are hardcoded in the frontend as a fixed option list. These are structural backend enums, not admin-configurable content — the "generic" requirement was scoped specifically to questions/templates/workflows, confirmed in conversation, not to every enum in the system. Everything inside a template (its questions, their type, their options, their validation, their visibility rule) is read from the API at runtime and never hardcoded.

## Left to the builder

- Exact React component tree and file layout (see the plan).
- Exact CSS/Tailwind styling choices, beyond "responsive, works on mobile and desktop."
- Exact wording of in-app error messages and the one-time onboarding walkthrough's copy.
- Whether the one-time new-patient walkthrough is a simple dismissible overlay or a dedicated first-run screen — left as an implementation choice, not specified further here given the time budget; a minimal version (a dismissible tooltip sequence on first login) satisfies the requirement.
