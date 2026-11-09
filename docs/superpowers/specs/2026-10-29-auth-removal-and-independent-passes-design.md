# Auth Removal, Session-List Landing, and Independent Family-Member Passes

## Intent and why

Three things today get in the way of how the patient portal is actually going to be used:

1. Account-based auth (signup/login/token) is overhead nobody needs right now — the proband is the only
   person at the keyboard, filling in their own data and every family member's data themselves. There's
   no second user to authenticate.
2. The landing experience forces a login/signup choice instead of just showing "here are the
   assessments in progress, pick one or start a new one."
3. The backend session model allows exactly **one active subject at a time** (`QuestionnaireSession.currentFamilyMember`
   + `returnTemplate`/`returnWorkflow`, a pause-and-resume pair). `AssessmentService.startFamilyMemberPass()`
   throws `ResourceInUseException` if a different family member's pass is already active, and
   `FamilyMemberService.delete()` does the same. In practice this means: once the proband starts filling in
   a relative's answers, they're stuck finishing that relative (or explicitly "ending" their pass) before they
   can switch to anyone else, including back to their own data. The proband said directly: this must not block —
   they want to freely bounce between their own section and any family member's section, in any order, and
   each one keeps its own place.

## Goals

- No accounts, tokens, email/password anywhere in the patient portal (frontend or backend).
- Landing page lists existing sessions by proband name, plus "Start new session."
- Clicking a session resumes it exactly where the proband left off.
- Adding/selecting a family member to work on happens inside the normal question flow (a dedicated
  workflow/template), not a standing sidebar panel.
- The proband can switch between their own section and any already-added family member's section at
  any time, in any order, and each subject's progress is independently preserved — no "finish this one
  first," no lock, no pause/return parking.

## Non-goals / anti-goals

- **Not building true multi-user concurrent writes.** Only one operator (the proband) is ever driving,
  possibly across different devices at different times, never two people editing the same subject's
  answers at the literal same instant. We are not adding optimistic locking, conflict resolution, or
  real-time sync. "Simultaneous" here means "freely interleavable," not "safe under concurrent writers."
- **Not giving family members their own login/link/device-of-their-own to resume their own section.**
  Explicitly ruled out by the proband this round — may come later, but isn't part of this work.
- **Not building a hub inside a family member's own pass for them to add *their* relatives.** All adding
  happens from the one hub in the proband's own walk, using the existing `scopeId` field (already
  supported by `FamilyMemberService.create()`) to anchor a new relative to any already-added family
  member, not just the proband. (E.g., adding a grandparent as a role scoped to the mother, without
  entering the mother's own pass.) This keeps the hub in one place. Revisit if it turns out to feel wrong
  in practice.
- Not touching the pedigree export, disease/mutation model, or anything from the earlier data-model work.
- Not deciding how an already-completed session's family-member-hub interacts with edits (FR-9 cascade
  invalidation) differently than it does today — same cascade logic applies, just scoped per subject as
  it already is.

## Current state (verified in code, 2026-10-29)

- `QuestionnaireSession` has `previousTemplate`/`previousWorkflow` (active subject's position),
  `currentFamilyMember` (null = proband active), `returnTemplate`/`returnWorkflow` (proband's parked
  position while a family member's pass is active).
- `AssessmentService.submitAnswer()` derives "who this answer is about" from `session.currentFamilyMember`,
  not from anything in the request (the request's `familyMemberId` is only a consistency check).
  `nextTemplate()`, `currentTemplate()`, `visitedTemplates()`, `viewVisitedTemplate()`, `resumeAtTemplate()`
  all work the same way — purely server-side session state, no subject parameter from the caller.
- Two workflows exist: `PERSONAL_DETAILS` (1 template: `DEMOGRAPHICS`, proband-only) and `FAMILY_HISTORY`
  (5 templates: `PARENTS`, `SIBLINGS`, `PARTNERS`, `CHILDREN`, `EXTENDED_FAMILY` — the proband/family
  member's own self-reported facts about their relatives, e.g. "Do you have siblings? How many?").
  Nothing in these templates is itself an "add a family member" action — that currently happens only
  through the separate `FamilyMemberPanel.jsx` sidebar component and its `POST .../family-members` call,
  which immediately starts a pass (`addFamilyMemberAndStartPass`).
- Auth: `AuthController`/`AuthService`/`AppUser`/`AuthToken` (Liquibase `018`), `AuthContext.jsx`,
  `LoginPage.jsx`/`SignupPage.jsx`, `ProtectedRoute.jsx`. `AssessmentPage.jsx` currently bootstraps by
  calling `getMe(token)` to find-or-create-and-link a session to the logged-in account.

## Design

### 1. Auth — removed completely

Backend: delete `AuthController`, `AuthService`, `AppUser`, `AuthToken`, `InvalidCredentialsException`,
and the CORS/bean wiring specific to them. A new Liquibase changeset (`019`) drops the `app_user` and
`auth_token` tables (additive-only history is preserved — we don't edit `018`, we add a changeset that
undoes it, consistent with how every other change in this codebase has been made).

Frontend: delete `AuthContext.jsx`, `ProtectedRoute.jsx`, `LoginPage.jsx`, `SignupPage.jsx`. `api/client.js`
drops `signup`/`login`/`getMe`/`updateSession` and the `Authorization` header entirely — every request
becomes anonymous, scoped only by `sessionId` (and now also `familyMemberId` where relevant, see below).

### 2. Landing page — session list

New page (`SessionListPage.jsx`) becomes the app's root route. It calls a new endpoint,
**`GET /api/v1/assessment/sessions`**, returning one row per `QuestionnaireSession`: `sessionId`,
a display name, `status`, `startedAt`. Display name is `Person.firstName + " " + Person.lastName` when
both are answered; until then (a session started but `DEMOGRAPHICS` not yet completed) it falls back to
something like `"Unnamed — started <date>"` (my call — nothing in the request specifies this, and it's a
cosmetic default, not a structural one).

Clicking an `IN_PROGRESS` row calls `POST /assessment/start` with that `sessionId` (unchanged endpoint,
already supports resuming by id) and lands on the assessment view at whatever template `previousTemplate`
already points to. "Start new session" calls the same endpoint with no `sessionId`, exactly as today.

A `COMPLETED` session still appears in the list (so the proband can find it again), but clicking it does
**not** call `/start` — `AssessmentService.start()` keeps its existing `IN_PROGRESS`-only resume filter
unchanged, consistent with `resumeAtTemplate` being the one deliberate, explicit escape hatch for
un-completing a session (per that method's own existing code comment). Instead, a completed row routes
straight to the existing `CompletionPage` (read-only, pedigree-export view) for that `sessionId`. Actually
re-opening a completed session for edits is unchanged from today (resume-at-template from within the
assessment view) — not something this work newly enables from the landing page, and out of scope here.

### 3. Family-member hub — a real workflow, not a sidebar

New workflow, code `FAMILY_MEMBERS`, name "Family Members", inserted at `display_order = 2` (pushing
`FAMILY_HISTORY` to `display_order = 3`). It has one template for now: code `FAMILY_MEMBER_HUB`,
`display_order = 1`, `subject_scope = PROBAND_ONLY` (see the non-goal above — the hub only ever appears
in the proband's own walk, never inside a family member's pass).

This template carries no ordinary `QUESTION` rows. The frontend recognizes `template.code ===
'FAMILY_MEMBER_HUB'` and renders a dedicated component instead of the generic question renderer — it
shows the list of family members added so far (each clickable, "Continue" taking the proband into that
person's own walk) and the existing add-family-member form (relationship type, maternal/paternal side,
and an anchor/"related to" picker using the existing `scopeId` field, defaulting to the proband).

Submitting the add-family-member form only adds the row to the list — it does **not** automatically enter
their walk. The proband then clicks "Continue" on that row, same as for any other already-added family
member, so there's one uniform interaction instead of special-casing the just-created one (and it lets the
proband queue up several relatives by name before going deep into any one of them). This retires today's
combined `addFamilyMemberAndStartPass` endpoint in favor of a plain create call from the hub, with
"continue" handled the same way for every row (see §4's `currentTemplate(familyMemberId=...)`).

Because
it has zero required questions, `validateTemplateCompletion`'s FR-6 check trivially passes and "next" just
moves on to `FAMILY_HISTORY` — but since it's a normal visited template, the proband can always jump back
to it later (existing `VisitedTemplatesNav`/`resumeAtTemplate` machinery, unchanged) to add more relatives
or continue one already added. No new navigation mechanism needed here.

### 4. Independent per-subject progress — the core change

Today there is exactly one "position" field pair in play at a time (`previousTemplate`/`previousWorkflow`
on the session), handed back and forth between the proband and whichever single family member is
"active," with `returnTemplate`/`returnWorkflow` used to park the proband's spot during the handoff. That
pair-of-state design is *why* only one subject can be in progress at once.

The fix: **give every subject its own, permanently-owned position**, and stop treating "who's active" as
session-level state at all.

- `QuestionnaireSession.previousTemplate`/`previousWorkflow` become the proband's position, full stop —
  never overwritten by a family member's pass, ever again.
- `FamilyMember` gains two new nullable columns: `current_template_id`, `current_workflow_id` (FKs,
  mirroring the session's own pair). `null` means that family member hasn't started their walk yet →
  entry point is the first `FAMILY_MEMBER_ONLY`/`BOTH` template, same resolution logic that exists today.
- `QuestionnaireSession.currentFamilyMember`, `returnTemplate`, `returnWorkflow` are deleted outright —
  nothing needs to "return" to anywhere, because the proband's spot was never touched in the first place.
- Every relevant `AssessmentService` method (`currentTemplate`, `submitAnswer`, `nextTemplate`,
  `visitedTemplates`, `viewVisitedTemplate`, `resumeAtTemplate`) takes an explicit, **required** `familyMemberId`
  parameter (null = proband) from the caller, instead of reading `session.currentFamilyMember`. Each call
  reads and writes the position for *that* subject only — the proband's row and a given family member's
  row are independent, so answering a question for the mother and, a minute later, answering one for the
  proband touch different rows and can genuinely interleave in any order without either walk's position
  ever being at risk of being clobbered by the other.
- `startFamilyMemberPass`'s `ResourceInUseException` ("finish it before starting another") goes away —
  there's no longer a single slot to contend for. Starting/continuing a family member's pass just means:
  look up their stored position (or resolve the entry template if null), return it. `FamilyMemberService.delete()`'s
  "currently active pass" check goes away too, for the same reason (the other two checks there — answered
  questions exist, or they're referenced by a relationship/role — still apply and are correct).
- `endFamilyMemberPass` (today's "restore the proband's parked position") is deleted with nothing standing
  in its place, because there's no longer a parked position to restore. When `nextTemplate(sessionId,
  familyMemberId)` exhausts a family member's applicable templates, it returns a subject-scoped "done"
  response — reusing `CurrentTemplateResponse`'s existing `completed` boolean (today only meant "whole
  session done"), but with `familyMemberId` populated so the frontend can tell "this relative's walk just
  finished" apart from "the whole session just finished" (same field, disambiguated by whether
  `familyMemberId` is null). This does **not** set `session.status`/`completedAt` — only the proband's own
  walk exhausting still does that. No new "is this family member done" column is needed; exhaustion is
  derived from `applicableTemplatesInOrder` exactly as it already is, consistent with this codebase's
  existing "derived, not stored" pattern (e.g. siblings/half-siblings at export time). On seeing a
  family-member-scoped `completed = true`, `TemplateView.jsx` navigates back to the `FAMILY_MEMBER_HUB`
  template rather than to `/completion`.
- Migration `019` (same changeset as the auth-table drop, or a separate one — builder's call) adds the two
  new `FAMILY_MEMBER` columns and drops the three retired `QUESTIONNAIRE_SESSION` columns.

This is a backend contract change: every assessment API call's shape changes to carry `familyMemberId`
explicitly rather than relying on hidden session state. `api/client.js` and every caller in the frontend
(`TemplateView.jsx`, `VisitedTemplatesNav.jsx`, etc.) need that parameter threaded through. This is the
single largest piece of work in this change and the part most likely to need test coverage rewritten
(`AssessmentFlowIntegrationTest`, `AnswerEditingIntegrationTest`, and anything else asserting today's
pause/resume behavior).

### 5. Frontend shape, roughly

- `App.jsx`: no `AuthProvider`/`ProtectedRoute`. Routes become `/` (session list), `/assessment/:sessionId`,
  `/completion`.
- `AssessmentPage.jsx`: no more `getMe`/`updateSession` bootstrap — it receives `sessionId` from the route
  (already resolved by the landing page's `start` call) and just renders `TemplateView`. The
  `bootstrapRef` StrictMode double-invoke guard from the earlier session-persistence bug fix is no longer
  needed for *account linking* (there's no account to link), but `TemplateView`'s own request-ordering
  guard for template fetches stays — that protects against a different race (stale fetches on subject
  switch), unrelated to auth.
- `FamilyMemberPanel.jsx` is deleted; its list/add logic moves into a new `FamilyMemberHub.jsx`, rendered
  by `TemplateView` only when the current template's code is `FAMILY_MEMBER_HUB`.
- Whatever subject the proband is currently viewing (proband or a specific family member) is just local UI
  state in `TemplateView`/`AssessmentPage` — set when they click into a family member from the hub,
  cleared when they navigate back to their own section — not anything persisted or "locked" server-side.

## What's left to the builder

- Exact column/endpoint names beyond what's specified above (e.g., whether the new changeset is one
  Liquibase file or two) — builder's call, follow the existing `0NN-verb-phrase.yaml` convention (next
  unused number is `019`).
- Exact wording/placement of the "unnamed session" placeholder label.
- Whether `GET /assessment/sessions` needs pagination — at current expected scale (one developer's test
  data), no; flag if real usage changes that.
- Test rewrites needed in `AssessmentFlowIntegrationTest`/`AnswerEditingIntegrationTest`/
  `EndToEndAssessmentTest`/the Playwright E2E spec for the new explicit-`familyMemberId` API shape and the
  removed auth endpoints — expected, not a surprise to flag back.
