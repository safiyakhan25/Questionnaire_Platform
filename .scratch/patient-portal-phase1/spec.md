# Patient Portal — Phase 1

Status: ready-for-agent

## Problem Statement

QuestionnairePlatform has a fully built, tested backend — admin-configurable workflows, templates and questions, patient-assessment execution, recursive family-member scoping, relationship persistence, and pedigree export — but a real patient has no way to use any of it. There is no account system and no frontend at all. A patient who needs to complete this genetic/family-history questionnaire alone at home, unsupervised, with no one present to explain a confusing step, currently cannot access the platform in any form.

## Solution

Build the first real, usable patient-facing web portal: a patient can create an account, log in, and be dropped straight into a questionnaire whose every screen is rendered live from the existing admin-configured workflow/template/question data — so a question added through the admin API shows up for the patient automatically, with no frontend change or redeploy. The patient can answer every supported question type, navigate forward and back (including editing an earlier answer), add a family member and personally fill in that relative's own answers by switching into their pass, and reach a confirmed end state where a valid pedigree export has been produced. All of this is layered on top of the existing assessment API without modifying it; only a new, minimal, additive authentication module is introduced alongside it.

## User Stories

1. As a patient, I want to create an account with my email (and optionally my phone number) and a password, so that my progress is saved under my own identity rather than lost.
2. As a patient, I want to log in with my email and password, so that I can resume my questionnaire instead of starting over.
3. As a patient, I want the app to find my in-progress assessment automatically when I log in, so that I never have to know or handle a session identifier myself.
4. As a patient logging in for the first time, I want a new assessment to be started for me automatically, so that I don't need to take any extra setup step.
5. As a patient, I want to be told clearly if the email I'm signing up with is already registered, so that I don't end up confused about which account is mine.
6. As a patient, I want to be told my login failed if I enter the wrong password, without being told whether the email itself is registered, so that my privacy is protected either way.
7. As a patient, I want every question to appear with the right kind of input — free text, a number field, a date picker, single-choice options, multiple-choice options, or a dropdown — so that I can actually answer it correctly.
8. As a patient, I want required questions to be visibly marked, so that I know what I must fill in before moving on.
9. As a patient, I want a question that only makes sense given my earlier answers to appear or disappear automatically, so that I'm never shown something irrelevant or confusing with no one around to explain it.
10. As a patient, I want the questionnaire to reflect any new question an administrator adds through the admin tools immediately, so that I always see the current set of questions.
11. As a patient answering a multiple-choice question, I want every option I check to be saved, so that picking more than one thing doesn't silently lose all but one of my choices.
12. As a patient, I want to move on to the next section once I've answered what's required, so that I make steady, visible progress.
13. As a patient who tries to move on too early, I want to be told exactly which required questions I still need to answer, so that I can fix it myself with no outside help.
14. As a patient, I want to go back to a section I already completed and change an answer, so that I can correct a mistake I notice later.
15. As a patient, I want my answers saved as I go, so that closing the browser or losing my connection never costs me any progress.
16. As a patient returning later or after a refresh, I want to land back exactly where I left off, so that I never wonder whether my earlier work survived.
17. As a patient, I want to add a family member — like my mother, my son, or my spouse — to my family history, so that my doctor gets a complete picture.
18. As a patient, I want to personally fill in that family member's own answers right after adding them, so that their information is captured even though they're not using the app themselves.
19. As a patient filling in a family member's answers, I want a clear, constant indicator of whose information I'm currently entering, so that I never accidentally answer as the wrong person with no one there to catch the mistake.
20. As a patient who finishes entering a family member's information, I want control to return to my own questionnaire automatically, so that I don't have to figure out how to switch back myself.
21. As a patient, I want to know unambiguously when I've completed the whole questionnaire, so that I'm confident there's nothing left to do.
22. As a patient, I want the system to confirm in the background that a valid family-tree record was produced from my answers, so that I know my doctor will receive something usable.
23. As a patient, I want to be able to try again myself if something goes wrong generating that final record, so that a temporary glitch doesn't leave me stuck with no next step.
24. As a patient, I want to complete the entire questionnaire just as easily on my phone as on a computer, so that I'm not forced to find a desktop to participate.
25. As a first-time patient, I want a brief, dismissible walkthrough of how the app works, so that I'm not left guessing, since I'm doing this alone.
26. As a patient, I want my password stored securely and never in plain text, so that my personal and medical information stays protected if the database is ever exposed.
27. As the platform's content administrator, I want questions, templates, and workflows I configure through the existing admin API to reach patients without any frontend engineering work, so that I can keep content current on my own.
28. As the engineering team, I want the new login module to be fully additive to the existing, already-tested assessment API, so that adding accounts carries no risk of regressing anything that already works.
29. As the engineering team, I want the backend and frontend covered at the seam that actually proves the integration works — not just each side in isolation — so that a real cross-origin or contract mismatch between them is caught before a patient hits it.
30. As a developer running this locally, I want the frontend to actually be able to reach the backend when the two are served from different addresses during development, so that the feature can be built and tested at all.

## Implementation Decisions

- A new, standalone user-account entity is added (email, unique; optional phone; hashed password; at most one linked assessment-session identifier) alongside a new opaque bearer-token entity, via a new database migration. No existing assessment-domain table, entity, or endpoint is modified.
- Passwords are hashed with BCrypt at the library's default cost factor. A minimal password-hashing-only dependency is added rather than a full security/filter-chain framework, since nothing else in this phase needs request-level security.
- Auth tokens are opaque random strings, consistent with how the existing assessment session identifier already works in this system. They do not expire and are not invalidated on a later login or on logout in this phase — there is no logout action in this phase.
- New, purely additive auth endpoints are introduced: register, log in, fetch my own profile (including my linked session id, or none), and link a freshly created session id to my account. Every existing assessment, family-member, and relationship-persistence endpoint is untouched and continues to be addressed by session id alone, with no authentication layered on top of it in this phase.
- Registering with an email that's already taken reuses the platform's existing "duplicate resource" error semantics (same status and response shape already used for other duplicate-prevention rules in this system), rather than introducing a parallel error type for this one case.
- A wrong password and a login attempt against an email that was never registered produce the identical error, so a failed login never discloses whether a given email is registered.
- Minimal cross-origin access is enabled for the frontend's development address. This was confirmed completely absent from the backend prior to this work; without it, the browser blocks every call the frontend makes, so this is required wiring, not optional polish.
- The questionnaire screen is driven entirely, at runtime, by each question's type, option list, validation rule, and visibility rule exactly as the existing API returns them — no question text, answer key, or option value is ever hardcoded in the frontend. The one deliberate exception: the family-relationship-type and maternal/paternal-side option lists are hardcoded, since they are structural backend enumerations, not admin-configurable content.
- Every question type the platform currently supports — free text, number, date, single-choice, multiple-choice, dropdown — renders with an appropriate control; a multiple-choice answer is always submitted as a list, even when only one option is selected.
- A blocked "move to next section" attempt surfaces the exact message the backend already produces (which names the specific missing required fields), rather than a generic error.
- Forward and backward navigation across the questionnaire's sections — including resuming a previously visited section to edit an answer — is built entirely on the backend's existing resume/visited-section capability; nothing new is needed on the backend for this.
- Adding a family member drops the patient directly into that relative's own pass through the questionnaire, reusing the backend's existing scope/pass-switching mechanism; no separate account, login, or shareable link for the relative exists in this phase (that remains a future phase). While inside a relative's pass, a persistent on-screen indicator names whose information is currently being entered; control and that indicator both revert to the patient automatically once that pass completes.
- At the end of the questionnaire, the frontend confirms a valid pedigree export can be produced by calling the existing export endpoint, without ever showing that raw record to the patient. A failure here gives the patient a way to retry that same check themselves rather than a dead end.
- A one-time, dismissible walkthrough introduces a first-time patient to the core actions (answering questions, adding a family member, going back to edit) and does not reappear on later visits.
- The application is fully usable at both typical phone and desktop widths.
- Explicitly deferred out of this change: shareable, token-based family-member access links and their lifecycle (expiry, revocation, wrongly-entered-data wipe, reissue); any admin-facing content-management screen; any doctor/clinician-facing screen or doctor-access grant; phone/email verification; password reset; logout and auth-token expiry/revocation; and authenticating the existing assessment/family-member/relationship endpoints themselves.

## Testing Decisions

- A good test here exercises observable behavior only: an HTTP request/response pair on the backend, or what a user can see and do on the frontend — never internal implementation details such as which internal function ran or a component's internal tree shape.
- **Backend seam**: new auth endpoints are tested through the full web layer and Spring context (the same convention every existing integration test in this codebase already uses), covering registration, login success and failure, duplicate-email rejection, session-linking, and the new cross-origin allowance.
- **Frontend seam**: each piece of the questionnaire UI is tested in isolation with component-rendering tests, mocking only the API-client module — the one clean boundary between the UI and the network. This covers dynamic rendering of every question type, required-field blocking using the backend's own error message, section navigation, session bootstrap/resume behavior, the family-member add-and-switch flow and its "whose information" indicator, and the completion screen's success/retry behavior.
- **End-to-end seam** (added in this round, beyond what the existing task-by-task implementation plan currently covers): a browser-driven end-to-end test runs the actual built frontend against a real running backend for the core path — register, land in the first section, answer through to completion, add one family member and fill in their data, and confirm the pedigree-export check succeeds. This is the only seam that can catch a real integration gap (a genuine cross-origin failure, a real request/response shape mismatch) that the mocked-boundary tests on either side can't see. This is new test infrastructure for this project; there is no prior frontend or end-to-end test here to follow, since no frontend has existed until now.
- Prior art: every existing service and relationship-persistence test in this codebase already uses the full-Spring-context integration style being extended here for auth.

## Out of Scope

- Shareable, token-based family-member access links and their full lifecycle (admin-configurable expiry, revocation, "true wipe" of wrongly-entered data, token reissue).
- Any admin-facing screen for managing workflows, templates, or questions.
- Any doctor/clinician-facing screen or patient-granted doctor access.
- Phone number verification and email verification.
- Password reset / forgot-password.
- A logout action, and any auth-token expiry or revocation.
- Authenticating or otherwise protecting the existing assessment, family-member, or relationship-persistence endpoints — they remain reachable by session id alone, exactly as today.
- Any change to an existing, already-tested assessment-domain table, entity, service, or endpoint.

## Further Notes

- This spec covers the same feature as, and should stay consistent with, the design document and task-by-task implementation plan already produced for "Patient Portal Phase 1" (`docs/superpowers/specs/2026-10-24-patient-portal-phase1-design.md` and `docs/superpowers/plans/2026-10-24-patient-portal-phase1.md`). The one real addition this spec introduces beyond that plan is the end-to-end browser-test seam, which is not yet reflected there as a task — add it before executing from that plan, if that's the plan being used.
- The original 2-day timeline for this feature was scoped against two seams, not three. Whoever picks this up should re-estimate the end-to-end seam's cost against that timeline before committing to it, since standing up browser-automation test infrastructure for the first time in this project is itself non-trivial work.
- No `GLOSSARY.md` or ADRs exist yet in this repository, so there was no established domain vocabulary or architecture decision record to check this spec against. Terms used here (proband, family member, pass, template, workflow, session) are taken directly from the existing codebase's own naming.
