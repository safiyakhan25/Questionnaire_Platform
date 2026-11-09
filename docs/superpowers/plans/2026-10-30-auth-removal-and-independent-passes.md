# Auth Removal, Session-List Landing, and Independent Family-Member Passes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove account-based auth entirely, replace the login/signup landing with a session list (resume-by-name or start new), move family-member add/continue into a real workflow template instead of a sidebar panel, and give the proband and every family member independently-preserved progress so switching between them never blocks or clobbers anyone's position.

**Architecture:** Backend: delete the auth vertical outright; move "current position" off the single session-level pause/resume pair onto two independent slots — `QuestionnaireSession` keeps the proband's own position untouched forever, `FamilyMember` gains its own `current_template_id`/`current_workflow_id`; every `AssessmentService` entry point takes an explicit `familyMemberId` (null = proband) instead of reading hidden session state. Frontend: session list becomes the root route; `TemplateView` renders a dedicated hub component when it lands on the `FAMILY_MEMBER_HUB` template; whichever subject is being viewed is plain local UI state, never persisted.

**Tech Stack:** Spring Boot 4.1 / Java 21 / Liquibase / PostgreSQL (backend), React 19 / Vite / Tailwind / Vitest / Playwright (frontend).

**Spec:** `docs/superpowers/specs/2026-10-29-auth-removal-and-independent-passes-design.md`

## Global Constraints

- Never run `git commit` or `git push` — the user commits everything herself. Every task's "stage for commit" step means `git add` only; do not run `git commit`, even though the step template below shows one.
- Liquibase changesets follow the existing `0NN-verb-phrase-in-kebab-case.yaml` convention, one `author: safiya` per changeset, a `rollback:` block. Next unused number is **`026`** (`019`–`025` were used by the pedigree data-model work after this spec was written — the spec itself leaves exact numbering to the builder).
- Workflow/Template/Question rows are never seeded via Liquibase in this codebase (confirmed: no migration inserts any `workflow`/`template`/`question` row) — they're created through the admin service layer (`WorkflowService`/`TemplateService`/`QuestionService`) at runtime. The new `FAMILY_MEMBERS`/`FAMILY_MEMBER_HUB` rows follow that same convention (Task 6), not a migration.
- Every backend change must leave `./mvnw test` fully green and the app booting clean (Hibernate `ddl-auto=validate` will catch any entity/schema mismatch immediately — confirmed a hard failure mode during the pedigree migration work).
- Every frontend change must leave `npm run test` fully green and `npm run lint` clean.

## Review Focus

- **Spoofed/stale `familyMemberId`:** submitting an answer, or asking for the current template, with a `familyMemberId` that doesn't belong to this session's proband must still be rejected — today this is an implicit side-effect of comparing against `session.currentFamilyMember`; in the new model it must be an explicit ownership check via `FamilyMemberService.findFamilyMemberOrThrow`.
- **First-time continue:** clicking "Continue" on a family member who has never answered anything (`current_template_id` is `null`) must resolve to their correct `FAMILY_MEMBER_ONLY`/`BOTH` entry template, not error or silently default to the proband's.
- **Position/template mismatch:** submitting an answer against a template that isn't the resolved subject's actual stored position must still be rejected (existing integrity check, re-pointed at the right entity instead of `session.getPreviousTemplate()`).
- **Completed session on the landing page:** a `COMPLETED` session must not be resumable via `/start` (unchanged backend behavior) and must route to the read-only completion view from the landing page, not attempt to resume into the question flow.
- **The actual promise of this feature — interleaving:** answer a question as the proband, switch to family member A and answer one of theirs, switch back to the proband — the proband's position must be exactly where it was, and switching back to family member A later must restore exactly where that pass was left, not the proband's position and not reset to A's entry template.

---

## File Structure

**Backend — deleted:**
- `AuthController.java`, `AuthService.java`, `AppUser.java`, `AuthToken.java`, `AppUserRepository.java`, `AuthTokenRepository.java`, `InvalidCredentialsException.java`, `SignupRequest.java`, `LoginRequest.java`, `UpdateSessionRequest.java`, `AuthResponse.java`, `AuthControllerIntegrationTest.java`

**Backend — modified:**
- `QuestionnaireSession.java` (drop 3 fields), `FamilyMember.java` (add 2 fields), `AssessmentService.java` (core refactor), `AssessmentController.java` (new `familyMemberId` params + new endpoint), `FamilyMemberController.java` (simplified `create`), `FamilyMemberService.java` (drop the active-pass delete-guard), `GlobalExceptionHandler.java` (drop the auth handler), `db.changelog-master.yaml`
- Tests: `AssessmentFlowIntegrationTest.java`, `AnswerEditingIntegrationTest.java`, `RelationshipPersistenceIntegrationTest.java`, `FamilyMemberIntegrationTest.java`, `PedigreeExportServiceTest.java`, `CrossWorkflowFamilyHistoryIntegrationTest.java`, `AdminConfigurationIntegrationTest.java`, `EndToEndAssessmentTest.java`, `InvalidRequestsIntegrationTest.java`

**Backend — created:**
- `026-replace-session-pause-resume-with-per-subject-position.yaml`, `SessionSummaryResponse.java` (DTO)

**Frontend — deleted:**
- `context/AuthContext.jsx` (+ `.test.jsx`), `components/ProtectedRoute.jsx` (+ `.test.jsx`), `pages/LoginPage.jsx` (+ `.test.jsx`), `pages/SignupPage.jsx` (+ `.test.jsx`), `components/FamilyMemberPanel.jsx` (+ `.test.jsx`)

**Frontend — modified:**
- `api/client.js` (+ `.test.js`), `App.jsx` (+ `.test.jsx`), `pages/AssessmentPage.jsx` (+ `.test.jsx`), `components/TemplateView.jsx` (+ `.test.jsx`), `components/VisitedTemplatesNav.jsx` (+ `.test.jsx`), `e2e/core-journey.spec.js`

**Frontend — created:**
- `pages/SessionListPage.jsx` (+ `.test.jsx`), `components/FamilyMemberHub.jsx` (+ `.test.jsx`)

---

### Task 1: Remove auth (backend)

**Files:**
- Delete: `entity/AppUser.java`, `entity/AuthToken.java`, `repository/AppUserRepository.java`, `repository/AuthTokenRepository.java`, `service/AuthService.java`, `controller/AuthController.java`, `exception/InvalidCredentialsException.java`, `dto/SignupRequest.java`, `dto/LoginRequest.java`, `dto/UpdateSessionRequest.java`, `dto/AuthResponse.java`, `src/test/.../web/AuthControllerIntegrationTest.java`
- Modify: `exception/GlobalExceptionHandler.java` (remove the `InvalidCredentialsException` `@ExceptionHandler` method, line ~33-37)
- Create: `src/main/resources/db/changelog/changes/026-replace-session-pause-resume-with-per-subject-position.yaml` (this task only adds the auth-table-drop changeset to it; Task 2 adds the rest to the same file)
- Modify: `db.changelog-master.yaml` (add the `026-...` include)

**Interfaces:**
- Produces: nothing downstream depends on auth — this is a pure deletion.

- [ ] **Step 1: Write the new changeset dropping `app_user`/`auth_token`**

```yaml
databaseChangeLog:
  - changeSet:
      id: 026-drop-app-user-and-auth-token
      author: safiya
      comment: Auth removed entirely - the proband is the only operator, no account to authenticate.
      changes:
        - dropTable:
            tableName: auth_token
        - dropTable:
            tableName: app_user
      rollback:
        - sql:
            sql: SELECT 1 -- irreversible without re-adding 018's full table definitions; not expected to be rolled back
```

- [ ] **Step 2: Add the include to `db.changelog-master.yaml`, run the app, confirm Liquibase drops both tables**

Run: start the backend (`./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=8081`), check the log for `Table auth_token dropped` / `Table app_user dropped`, then stop it.

- [ ] **Step 3: Delete the 11 auth files and the `InvalidCredentialsException` handler listed above**

- [ ] **Step 4: Run the full backend suite**

Run: `./mvnw test`
Expected: compiles clean, `AuthControllerIntegrationTest` is gone, all remaining tests pass (118 - 5 = 113, before Task 4's additions).

- [ ] **Step 5: Stage for commit (do not commit)**

```bash
git add -A
```

---

### Task 2: Independent per-subject progress — schema and entities

**Files:**
- Modify: `026-replace-session-pause-resume-with-per-subject-position.yaml` (add two more changesets to the file Task 1 started), `entity/QuestionnaireSession.java`, `entity/FamilyMember.java`

**Interfaces:**
- Consumes: nothing new.
- Produces: `FamilyMember.getCurrentTemplate()/.setCurrentTemplate(Template)`, `.getCurrentWorkflow()/.setCurrentWorkflow(Workflow)` — Task 3 reads/writes these directly. `QuestionnaireSession` keeps `getPreviousTemplate()/setPreviousTemplate()`, `getPreviousWorkflow()/setPreviousWorkflow()` unchanged; `getCurrentFamilyMember()`, `getReturnTemplate()`, `getReturnWorkflow()` and their setters no longer exist — any remaining caller is a compile error Task 3/4 must fix.

- [ ] **Step 1: Add the schema changesets**

Append to the same `026-...yaml` file from Task 1:

```yaml
  - changeSet:
      id: 026-drop-session-pause-resume-columns
      author: safiya
      comment: >
        Replaces the single active-subject pause/resume pair with per-subject position (see next
        changeset) - the proband's own previous_template_id/previous_workflow_id are untouched by
        this change and now permanently belong only to the proband.
      changes:
        - dropColumn:
            tableName: questionnaire_session
            columnName: current_family_member_id
        - dropColumn:
            tableName: questionnaire_session
            columnName: return_template_id
        - dropColumn:
            tableName: questionnaire_session
            columnName: return_workflow_id
      rollback:
        - addColumn:
            tableName: questionnaire_session
            columns:
              - column:
                  name: current_family_member_id
                  type: UUID
              - column:
                  name: return_template_id
                  type: UUID
              - column:
                  name: return_workflow_id
                  type: UUID

  - changeSet:
      id: 026-add-family-member-position-columns
      author: safiya
      comment: >
        Each family member now owns its own position, independent of the proband's and of every
        other family member's - null means that family member's pass hasn't started yet.
      changes:
        - addColumn:
            tableName: family_member
            columns:
              - column:
                  name: current_template_id
                  type: UUID
                  constraints:
                    foreignKeyName: fk_family_member_current_template
                    references: template(id)
              - column:
                  name: current_workflow_id
                  type: UUID
                  constraints:
                    foreignKeyName: fk_family_member_current_workflow
                    references: workflow(id)
      rollback:
        - dropColumn:
            tableName: family_member
            columnName: current_workflow_id
        - dropColumn:
            tableName: family_member
            columnName: current_template_id
```

- [ ] **Step 2: Update `QuestionnaireSession.java`** — remove the `currentFamilyMember`, `returnTemplate`, `returnWorkflow` fields (and their `@ManyToOne`/`@JoinColumn` annotations) entirely. Leave `previousTemplate`/`previousWorkflow` untouched.

- [ ] **Step 3: Update `FamilyMember.java`** — add, mirroring `QuestionnaireSession`'s existing pattern:

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "current_template_id")
private Template currentTemplate;

@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "current_workflow_id")
private Workflow currentWorkflow;
```

- [ ] **Step 4: Run the app and confirm it fails to compile** (expected — `AssessmentService`/`FamilyMemberService` still reference the removed fields; this is the red state Task 3 turns green)

Run: `./mvnw compile`
Expected: FAILURE, errors naming `getCurrentFamilyMember`/`getReturnTemplate`/`getReturnWorkflow` as undefined — confirms the field removal took effect.

- [ ] **Step 5: Stage for commit (do not commit)**

---

### Task 3: Independent per-subject progress — AssessmentService/AssessmentController rewrite

**Files:**
- Modify: `service/AssessmentService.java`, `controller/AssessmentController.java`, `service/FamilyMemberService.java`

**Interfaces:**
- Consumes: `FamilyMember.getCurrentTemplate()/setCurrentTemplate()/getCurrentWorkflow()/setCurrentWorkflow()` (Task 2). `FamilyMemberService.findFamilyMemberOrThrow(UUID sessionId, UUID familyMemberId)` (already exists, package-private — used here to validate ownership before reading/writing a family member's position).
- Produces (new/changed `AssessmentService` public signatures; Task 4's tests and the frontend call these):
  - `currentTemplate(UUID sessionId, UUID familyMemberId)`
  - `submitAnswer(UUID sessionId, AnswerSubmitRequest request)` — signature unchanged, but `request.familyMemberId()` is now authoritative, not a consistency check
  - `nextTemplate(UUID sessionId, UUID familyMemberId)`
  - `visitedTemplates(UUID sessionId, UUID familyMemberId)`
  - `viewVisitedTemplate(UUID sessionId, UUID templateId, UUID familyMemberId)`
  - `resumeAtTemplate(UUID sessionId, UUID templateId, UUID familyMemberId)`
  - Removed: `addFamilyMemberAndStartPass`, `startFamilyMemberPass`, `endFamilyMemberPass` (private)
  - `start(AssessmentStartRequest request)` — unchanged, always proband-scoped

**Design notes for the implementer:**
- Replace every read of `session.getCurrentFamilyMember()` with the `familyMemberId` parameter now passed into each method. Replace `resolveSubject(session)` with a variant that takes `familyMemberId` directly instead of reading it off the session.
- Introduce a single position-resolution point used by `currentTemplate`, `nextTemplate`, and anywhere else that needs "where is this subject right now, loading their entry template if they've never started": given `(session, familyMemberId)`, when `familyMemberId == null` behave exactly as `loadOrResolveEntryTemplate(session)` already does (reading/writing `session.previousTemplate`/`previousWorkflow`); when non-null, call `familyMemberService.findFamilyMemberOrThrow(session.getId(), familyMemberId)` first (this is what catches a spoofed/foreign id — Review Focus item 1), then read/write that `FamilyMember`'s own `currentTemplate`/`currentWorkflow`, resolving via `resolveFirstApplicableTemplate(true)` when null (Review Focus item 2).
- `nextTemplate`'s family-member-exhausted branch (today's `endFamilyMemberPass` call) is deleted outright. In its place, return `completedResponse(session)`-shaped response but with `familyMemberId` populated (add an overload or parameter to `completedResponse` for this) and **do not** touch `session.status`/`completedAt`.
- `submitAnswer`'s ownership/position check (today: compare `request.familyMemberId()` against `session.getCurrentFamilyMember()`) becomes: resolve the subject's stored current template via the same position-resolution point, and reject if it doesn't match `question.getTemplate()` — this is Review Focus item 3, same failure shape as today's check, just sourced from the right place.
- `FamilyMemberService.delete()`: remove the `session.getCurrentFamilyMember() != null && ...equals(familyMemberId)` guard block (lines ~288-291 of the current file) — the field it reads no longer exists. The two remaining guards (answered questions exist; referenced by a relationship/role) are untouched.
- `AssessmentController`: add `@RequestParam(required = false) UUID familyMemberId` to `currentTemplate`, `nextTemplate`, `visitedTemplates`, `viewVisitedTemplate`, `resumeAtTemplate` and pass it through. `submitAnswer` is unchanged (already carries it in the body).

- [ ] **Step 1: Write the failing interleaving test first (Review Focus item 5 — the core promise of this change)**

In `AssessmentFlowIntegrationTest.java`:

```java
@Test
void probandAndFamilyMemberPositionsAreIndependentAcrossInterleavedSwitches() {
    UUID sessionId = startSession();
    // proband answers first question, advances once
    assessmentService.submitAnswer(sessionId, new AnswerSubmitRequest(firstNameQuestionId, textNode("Safiya"), null));
    CurrentTemplateResponse probandAtSiblings = assessmentService.nextTemplate(sessionId, null);

    FamilyMemberResponse mother = familyMemberService.create(sessionId,
            new FamilyMemberCreateRequest(RelationshipType.MOTHER, MaternalPaternalSideType.MATERNAL));
    CurrentTemplateResponse motherEntry = assessmentService.currentTemplate(sessionId, mother.id());
    assessmentService.submitAnswer(sessionId, new AnswerSubmitRequest(motherFirstNameQuestionId, textNode("Jane"), mother.id()));
    CurrentTemplateResponse motherAdvanced = assessmentService.nextTemplate(sessionId, mother.id());

    // switch back to the proband - must be exactly where they left off, not reset, not mother's position
    CurrentTemplateResponse probandAgain = assessmentService.currentTemplate(sessionId, null);
    assertThat(probandAgain.templateId()).isEqualTo(probandAtSiblings.templateId());

    // switch back to the mother - must be exactly where HER pass left off, not re-entered at her start
    CurrentTemplateResponse motherAgain = assessmentService.currentTemplate(sessionId, mother.id());
    assertThat(motherAgain.templateId()).isEqualTo(motherAdvanced.templateId());
}
```

- [ ] **Step 2: Run it, confirm it fails to compile** (old method signatures don't take `familyMemberId`)

Run: `./mvnw test -Dtest=AssessmentFlowIntegrationTest`
Expected: COMPILE FAILURE

- [ ] **Step 3: Implement the `AssessmentService`/`AssessmentController`/`FamilyMemberService` changes described above**

- [ ] **Step 4: Run the test, confirm it passes**

Run: `./mvnw test -Dtest=AssessmentFlowIntegrationTest`
Expected: PASS

- [ ] **Step 5: Stage for commit (do not commit)**

---

### Task 4: Fix the remaining backend tests for the new explicit-`familyMemberId` API

**Files:**
- Modify: `AssessmentFlowIntegrationTest.java` (remaining tests beyond Task 3's new one), `AnswerEditingIntegrationTest.java`, `RelationshipPersistenceIntegrationTest.java`, `FamilyMemberIntegrationTest.java`, `PedigreeExportServiceTest.java`, `CrossWorkflowFamilyHistoryIntegrationTest.java`, `AdminConfigurationIntegrationTest.java`, `web/EndToEndAssessmentTest.java`, `web/InvalidRequestsIntegrationTest.java`

**Interfaces:**
- Consumes: Task 3's new `AssessmentService` signatures and the removal of `addFamilyMemberAndStartPass`/`startFamilyMemberPass`.

- [ ] **Step 1: Run the full suite to get the current failure list**

Run: `./mvnw test`
Expected: FAILURE — compile errors in the 8 files above, naming the old method signatures/removed methods.

- [ ] **Step 2: Fix each file's call sites** — every `assessmentService.currentTemplate(sessionId)` → `currentTemplate(sessionId, familyMemberId)` (pass `null` for proband-scoped calls, the relevant family member's id otherwise); every `addFamilyMemberAndStartPass(...)` call replaced with `familyMemberService.create(...)` followed by `assessmentService.currentTemplate(sessionId, created.id())` to get the equivalent "first template of their pass" response the test previously asserted on. For `InvalidRequestsIntegrationTest`/`EndToEndAssessmentTest` (HTTP-level), add `?familyMemberId=...` query params to the relevant `mockMvc.perform(...)` calls where the old implicit-session-state test exercised a family member.

- [ ] **Step 3: Run the full suite again**

Run: `./mvnw test`
Expected: PASS, all tests green.

- [ ] **Step 4: Stage for commit (do not commit)**

---

### Task 5: Simplify family-member creation + add the sessions-list endpoint

**Files:**
- Modify: `controller/FamilyMemberController.java`, `service/AssessmentService.java`, `repository/QuestionnaireSessionRepository.java`, `controller/AssessmentController.java`
- Create: `dto/SessionSummaryResponse.java`

**Interfaces:**
- Produces: `GET /api/v1/assessment/sessions` → `List<SessionSummaryResponse>`; `POST /api/v1/assessment/{sessionId}/family-members` now returns `FamilyMemberResponse` (201) instead of `CurrentTemplateResponse`.

- [ ] **Step 1: Write the failing test for the sessions list**

New test in `AssessmentFlowIntegrationTest.java`:

```java
@Test
void listSessionsReturnsDisplayNameFromPersonOrPlaceholderWhenUnnamed() {
    UUID namedSessionId = startSession();
    assessmentService.submitAnswer(namedSessionId, new AnswerSubmitRequest(firstNameQuestionId, textNode("Safiya"), null));
    UUID unnamedSessionId = startSession();

    List<SessionSummaryResponse> sessions = assessmentService.listSessions();

    assertThat(sessions).extracting(SessionSummaryResponse::sessionId)
            .contains(namedSessionId, unnamedSessionId);
    SessionSummaryResponse named = sessions.stream().filter(s -> s.sessionId().equals(namedSessionId)).findFirst().orElseThrow();
    assertThat(named.displayName()).contains("Safiya");
    SessionSummaryResponse unnamed = sessions.stream().filter(s -> s.sessionId().equals(unnamedSessionId)).findFirst().orElseThrow();
    assertThat(unnamed.displayName()).startsWith("Unnamed");
}
```

- [ ] **Step 2: Run it, confirm it fails to compile** (`SessionSummaryResponse`/`listSessions` don't exist)

- [ ] **Step 3: Create `SessionSummaryResponse(UUID sessionId, String displayName, SessionStatus status, LocalDateTime startedAt)`; add `QuestionnaireSessionRepository.findAllByOrderByStartedAtDesc()`; add `AssessmentService.listSessions()`** — display name is `firstName + " " + lastName` trimmed when either is present, else `"Unnamed — started " + startedAt.toLocalDate()`.

- [ ] **Step 4: Add `GET /sessions` to `AssessmentController`, mapped before `/{sessionId}`:**

```java
@GetMapping("/sessions")
public ResponseEntity<List<SessionSummaryResponse>> sessions() {
    return ResponseEntity.ok(assessmentService.listSessions());
}
```

- [ ] **Step 5: Run the test, confirm it passes**

- [ ] **Step 6: Simplify `FamilyMemberController.create()`** — drop the `AssessmentService` dependency, call `familyMemberService.create(sessionId, request)` directly, return `FamilyMemberResponse` with `HttpStatus.CREATED`. Update any test asserting the old `CurrentTemplateResponse` shape from this endpoint (check `FamilyMemberIntegrationTest`/`EndToEndAssessmentTest`).

- [ ] **Step 7: Run the full suite**

Run: `./mvnw test`
Expected: PASS

- [ ] **Step 8: Stage for commit (do not commit)**

---

### Task 6: Seed `FAMILY_MEMBERS` workflow and `FAMILY_MEMBER_HUB` template in the dev database

This is a one-time data action against the running dev backend, not a code change — matches how `PERSONAL_DETAILS`/`FAMILY_HISTORY` were created (confirmed: no migration seeds workflow/template/question rows anywhere in this codebase).

**Interfaces:**
- Produces: a real `workflow` row (`code: FAMILY_MEMBERS`, `display_order: 2`) and `template` row (`code: FAMILY_MEMBER_HUB`, `display_order: 1`, `subject_scope: PROBAND_ONLY`, zero questions) in `questionnaire_platform_final`. `FAMILY_HISTORY`'s `display_order` moves from `2` to `3` so it still comes after.

- [ ] **Step 1: With the dev backend running on 8081, call the existing admin API** (`WorkflowController`/`TemplateController`, same ones `TestFixtures` wraps for tests):

```bash
curl -X PUT http://localhost:8081/api/v1/workflows/<family-history-workflow-id> -d '{"displayOrder":3,...}'  # bump FAMILY_HISTORY out of the way first
curl -X POST http://localhost:8081/api/v1/workflows -d '{"code":"FAMILY_MEMBERS","name":"Family Members","displayOrder":2}'
curl -X POST http://localhost:8081/api/v1/workflows/<new-id>/templates -d '{"code":"FAMILY_MEMBER_HUB","name":"Family Members","displayOrder":1,"subjectScope":"PROBAND_ONLY"}'
```

(Exact request bodies follow whatever `WorkflowCreateRequest`/`TemplateCreateRequest`/the corresponding update DTOs actually require — check those DTOs before running; this step is data entry, not a code change, so there's no test to write for it.)

- [ ] **Step 2: Verify via `psql`** that `workflow`/`template` now show `FAMILY_MEMBERS`/`FAMILY_MEMBER_HUB` with the right `display_order`s and `FAMILY_HISTORY` is still after it.

---

### Task 7: Remove auth (frontend) + session-list landing page + `App.jsx` rewire

**Files:**
- Delete: `context/AuthContext.jsx`, `context/AuthContext.test.jsx`, `components/ProtectedRoute.jsx`, `components/ProtectedRoute.test.jsx`, `pages/LoginPage.jsx`, `pages/LoginPage.test.jsx`, `pages/SignupPage.jsx`, `pages/SignupPage.test.jsx`
- Modify: `api/client.js`, `api/client.test.js`, `App.jsx`, `App.test.jsx`, `pages/AssessmentPage.jsx`, `pages/AssessmentPage.test.jsx`
- Create: `pages/SessionListPage.jsx`, `pages/SessionListPage.test.jsx`

**Interfaces:**
- Produces: `getSessions()` in `api/client.js` → `GET /assessment/sessions`. `SessionListPage` renders a list + "Start new session" button, navigating to `/assessment/:sessionId` (resume/start) or `/completion/:sessionId` (completed rows). `AssessmentPage` reads `sessionId` from `useParams()`.

- [ ] **Step 1: Write `SessionListPage.test.jsx`** — asserts: fetches `getSessions()` on mount; renders each session's `displayName` as a clickable item; an `IN_PROGRESS` row navigates to `/assessment/:sessionId` after calling `startAssessment({ sessionId })`; a `COMPLETED` row navigates straight to `/completion/:sessionId` with no `startAssessment` call; a "Start new session" button calls `startAssessment({ sessionId: null })` and navigates to `/assessment/:newSessionId`.

- [ ] **Step 2: Run it, confirm it fails** (component doesn't exist)

- [ ] **Step 3: In `api/client.js`** — delete `signup`, `login`, `getMe`, `updateSession`; delete the `token`/`Authorization` handling in `request()`; add `export const getSessions = () => request('/assessment/sessions');`. Update `client.test.js` to match (delete the auth-function tests, add one for `getSessions`).

- [ ] **Step 4: Implement `SessionListPage.jsx`** per Step 1's assertions, using `getSessions`/`startAssessment` from `api/client.js` and `useNavigate` from `react-router-dom`.

- [ ] **Step 5: Run `SessionListPage.test.jsx`, confirm it passes**

- [ ] **Step 6: Delete the 4 auth files and their tests listed above.**

- [ ] **Step 7: Rewrite `App.jsx`** — no `AuthProvider`/`ProtectedRoute`. Routes: `/` → `SessionListPage`, `/assessment/:sessionId` → `AssessmentRoute` (reads `sessionId` via `useParams`), `/completion/:sessionId` → `CompletionRoute` (reads `sessionId` via `useParams` instead of `location.state`, so a direct/refreshed link still works). Update `App.test.jsx` accordingly.

- [ ] **Step 8: Rewrite `AssessmentPage.jsx`** — remove the `useAuth`/`getMe`/`updateSession`/`bootstrapRef` bootstrap effect entirely; take `sessionId` as a prop (from the route). Keep rendering `TemplateView` (Task 9 adds the hub-switching state here). Update `AssessmentPage.test.jsx` to drop auth mocking and just assert it renders `TemplateView` with the given `sessionId`.

- [ ] **Step 9: Run the full frontend suite**

Run: `npm run test`
Expected: PASS

- [ ] **Step 10: Stage for commit (do not commit)**

---

### Task 8: Thread `familyMemberId` through the frontend + the "back to family members" affordance

**Files:**
- Modify: `api/client.js`, `api/client.test.js`, `components/TemplateView.jsx`, `components/TemplateView.test.jsx`, `components/VisitedTemplatesNav.jsx`, `components/VisitedTemplatesNav.test.jsx`

**Interfaces:**
- Produces: `getCurrentTemplate(sessionId, familyMemberId)`, `nextTemplate(sessionId, familyMemberId)`, `getVisitedTemplates(sessionId, familyMemberId)`, `viewVisitedTemplate(sessionId, templateId, familyMemberId)`, `resumeAtTemplate(sessionId, templateId, familyMemberId)` — each appends `?familyMemberId=...` only when non-null.
- Consumes (from Task 9): `TemplateView` gains an `onBackToFamilyMembers` prop, called when the proband clicks the new "← Back to Family Members" link while `familyMemberId != null`.

- [ ] **Step 1: Update `api/client.js`'s 5 functions above to accept and forward `familyMemberId`.** Update `client.test.js` to assert the query param is appended when present and omitted when `null`/undefined.

- [ ] **Step 2: Write the failing test for `TemplateView`'s subject-done handling** — when `nextTemplate` resolves with `{ completed: true, familyMemberId: <same id> }`, `TemplateView` must call `onFamilyMemberPassEnded` (unchanged prop name/contract) rather than `onCompleted`. Add a test for the new "← Back to Family Members" link appearing only when `familyMemberId != null` and calling the new `onBackToFamilyMembers` prop on click.

- [ ] **Step 3: Run it, confirm it fails**

- [ ] **Step 4: Implement in `TemplateView.jsx`** — thread `familyMemberId` into every `api/client.js` call it makes; add the "← Back to Family Members" link inside the existing blue "You are now answering for: X" banner, calling `onBackToFamilyMembers()`.

- [ ] **Step 5: Update `VisitedTemplatesNav.jsx`** to accept and forward `familyMemberId` to `getVisitedTemplates`/`resumeAtTemplate`.

- [ ] **Step 6: Run the frontend suite, confirm it passes**

- [ ] **Step 7: Stage for commit (do not commit)**

---

### Task 9: Family-member hub component

**Files:**
- Delete: `components/FamilyMemberPanel.jsx`, `components/FamilyMemberPanel.test.jsx`
- Create: `components/FamilyMemberHub.jsx`, `components/FamilyMemberHub.test.jsx`
- Modify: `components/TemplateView.jsx`, `components/TemplateView.test.jsx`, `pages/AssessmentPage.jsx`, `pages/AssessmentPage.test.jsx`

**Interfaces:**
- Consumes: `getFamilyMembers(sessionId)`, `addFamilyMember(sessionId, { relationshipType, maternalPaternalSide, scopeId })` (both already exist in `api/client.js`, unchanged).
- Produces: `FamilyMemberHub({ sessionId, onContinue })` — `onContinue(familyMemberId, label)` fires when the proband clicks "Continue" on a row. Submitting the add form only refreshes the list (no `onContinue` call — see the design's "add just adds" decision).

- [ ] **Step 1: Write `FamilyMemberHub.test.jsx`** — asserts: fetches and renders `getFamilyMembers(sessionId)` on mount, each with a "Continue" button; submitting the add form calls `addFamilyMember` and re-fetches the list, **without** calling `onContinue`; clicking "Continue" on a row calls `onContinue(member.id, label)`.

- [ ] **Step 2: Run it, confirm it fails** (component doesn't exist)

- [ ] **Step 3: Implement `FamilyMemberHub.jsx`**, adapted from `FamilyMemberPanel.jsx`'s existing list/add markup and `RELATIONSHIP_TYPES`/`MATERNAL_PATERNAL_SIDES` constants, dropping the auto-switch-on-add behavior.

- [ ] **Step 4: Run it, confirm it passes**

- [ ] **Step 5: Write the failing test for `TemplateView` rendering the hub** — when `template.templateCode === 'FAMILY_MEMBER_HUB'`, `TemplateView` renders `FamilyMemberHub` instead of the `QuestionField` list.

- [ ] **Step 6: Implement in `TemplateView.jsx`**, wiring `FamilyMemberHub`'s `onContinue` to a new `onContinueFamilyMember(familyMemberId, label)` prop so the parent (`AssessmentPage`) can switch which subject is active.

- [ ] **Step 7: Delete `FamilyMemberPanel.jsx`/`.test.jsx`.**

- [ ] **Step 8: Update `AssessmentPage.jsx`** — hold `familyMemberId`/`subjectLabel` as local state (same shape as today's, just no longer initialized via a sidebar panel); pass `onContinueFamilyMember` (sets the state), `onFamilyMemberPassEnded` (clears it back to proband — per the design, this means re-fetching the proband's own actual position, which in practice is usually still the hub), and `onBackToFamilyMembers` (also clears it back to proband) into `TemplateView`.

- [ ] **Step 9: Run the full frontend suite**

Run: `npm run test`
Expected: PASS

- [ ] **Step 10: Stage for commit (do not commit)**

---

### Task 10: Update the Playwright E2E spec

**Files:**
- Modify: `e2e/core-journey.spec.js`, `e2e/helpers.js` (if it contains login/signup helpers)

**Interfaces:**
- Consumes: the full stack from Tasks 1-9, running live.

- [ ] **Step 1: Remove the signup/login steps from `core-journey.spec.js`'s walkthrough; start from the landing page's "Start new session" button instead.**

- [ ] **Step 2: Add a step exercising the family-member hub** — navigate to the `FAMILY_MEMBERS` workflow's template, add a family member via the hub form, click "Continue," answer at least one question as that family member, click "← Back to Family Members," confirm the proband's own position is unaffected.

- [ ] **Step 3: Run the E2E spec against the real running backend+frontend**

Run: `npx playwright test e2e/core-journey.spec.js`
Expected: PASS

- [ ] **Step 4: Stage for commit (do not commit)**

---

## Self-Review

**1. Spec coverage:** §1 (auth removal) → Tasks 1, 7. §2 (session list) → Tasks 5, 7. §3 (hub workflow/template) → Tasks 6, 9. §4 (independent progress, the core change) → Tasks 2, 3, 4, 8. §5 (frontend shape) → Tasks 7, 8, 9. All five sections have at least one owning task.

**2. Step scan:** checked — no step says "handle edge cases" or leaves a type undefined; every code step carries an exact signature or exact diff location, every test step carries concrete assertions or named test code.

**3. Type consistency:** `familyMemberId` is `UUID`, nullable, named identically in every signature across Tasks 3, 4, 5, 8, 9 — no renaming drift. `FamilyMemberHub`'s `onContinue` becomes `TemplateView`'s `onContinueFamilyMember` deliberately (different components, different prop name is intentional, not a typo) — flagged here so it isn't "fixed" into a mismatch during execution.

**4. Review Focus:** all five items have an owning task and either a named test (items 1, 2, 3, 5 — Task 3 Steps 1-4, Task 4) or an explicit design constraint restated at the point it's implemented (item 4 — Task 7 Step 1's `SessionListPage` test, Task 5's unchanged `start()` filter).

**5. Proportion:** this plan is close in length to the spec because the spec's §4 is itself the bulk of the real work (a genuine architecture change touching ~15 files); task bodies here are signatures/diffs/test assertions, not full method implementations.

---

Plan complete and saved to `docs/superpowers/plans/2026-10-30-auth-removal-and-independent-passes.md`. Please review the plan. Which execution approach would you prefer?

- **Subagent-driven** - A fresh subagent implements each task and a fresh reviewer checks it before the next one starts, then a whole-branch review at the end. Most thorough; costs a fresh context per task and per review.
- **Native** - I implement every task myself in this session, the way this harness runs work, then one fresh reviewer on the most capable model checks the whole branch. Cheapest and fastest; no independent review until the end. Runs well with a mid-tier session model, since the plan carries the design.

For this plan I recommend **Native**, because almost every task in it is a direct dependency chain, not independent work — Task 3 rewrites the exact method signatures Task 4's tests call, Task 7's routes are what Task 9's `AssessmentPage` changes build on, and so on; there's very little here a parallel subagent could do without first seeing the previous task's exact output. Does the plan capture what you want, and which approach should we use?
