# Pedigree Viewer & Post-Completion Editing — Design

## Intent and why

Right now the assessment collects everything needed for a pedigree, but nobody ever sees a
pedigree — the only artifact is the raw JSON from `GET /pedigree-export`. Safiya wants the
actual diagram visible in two moments: a quick, low-commitment peek while answering ("am I
building this family tree right so far?"), and a proper destination once the assessment is
done. She also wants the "done" state to not be a dead end — real intake data always needs
correction, so editing has to stay available indefinitely, with no extra ceremony to reopen
or re-close the session.

This also finally exercises the `pedigree-export` endpoint for real: it was built and tested
against the PedigreeDraw schema months ago but has had no caller until now.

## What PedigreeDraw actually is (recon findings)

`C:\Users\nonst\Downloads\PedigreeDraw\PedigreeDraw` is a separate, proprietary-licensed Java 21
Maven project, unrelated to this repo, not currently built. Key facts that drove this design:

- It is **not a library to embed** — it's a standalone service/CLI pair. The relevant front-end
  is `pedigree-http`: a minimal `jdk.httpserver`-based HTTP service, no third-party web framework,
  no CORS support documented anywhere in its README or module notes.
- `POST /render` (optionally `?format=svg|png|pdf`, default SVG) takes the **exact JSON body**
  our `PedigreeExportService` already produces — same `pedigree_schema.json` shape, and we already
  emit `schemaVersion: "4.0.0"` to match it. (PedigreeDraw's schema only requires `schemaVersion`
  to be *some* string, not specifically `"4.0.0"` — so this is a match by what we emit today, not
  a constraint PedigreeDraw itself enforces. Worth knowing if either side's schema version ever
  drifts.) No transformation needed on our side.
- It validates the document against that schema before rendering and returns `422
  application/problem+json` with per-field JSON Pointer errors if it's invalid; `200` with the
  image bytes on success; `500` on an internal render failure.
- It is not built yet. Standing it up means `cd pedigree-tool && mvn verify`, then running
  `pedigree-http-<version>-uber.jar` as its own JVM process. It needs a `config/` + `schema/`
  directory pair on its working directory (or `--app-config`/`PEDIGREE_APP_CONFIG`).
- Default port is 8080, which this repo's Docker already occupies in dev — it must run on a
  different port here.
- It has no persistence and no auth — it's a pure render function: JSON in, image bytes out.

**Conclusion:** this has to be a backend-to-backend integration, not a browser-to-PedigreeDraw
call. The QuestionnairePlatform backend proxies: build the export JSON (already-existing code),
POST it to PedigreeDraw, stream the image bytes back to the frontend under our own
already-CORS-configured API. This also means PedigreeDraw is never exposed to the internet or to
the browser directly, and we get to translate its error shapes into something the frontend can
show sensibly.

## Goals

- A "Pedigree Viewer" button on the assessment screen that renders the pedigree **as filled in so
  far** in a lightweight, dismissible way, without leaving the question flow.
- A proper, dedicated view of the **final** pedigree once the assessment is complete.
- Permanent ability to go back into a completed assessment and add or correct information, with
  no "reopen" or "re-finish" step — the existing forward flow already re-completes it.

## Non-goals

- Editing the diagram itself (layout, dragging nodes, manual corrections to the rendering) — the
  diagram is a read-only rendering of the questionnaire data; corrections happen by editing
  answers, not the picture.
- Packaging/deploying PedigreeDraw for production, CI integration, or Docker-izing it. This
  design only covers making it reachable from a locally-running backend, the same way Postgres
  and the two existing dev servers are already run manually.
- Changing what data the pedigree-export JSON contains. That was already decided in the
  2026-11-01 structured-pedigree-questions work (including the decision to leave `race` /
  `marital_status` / `blood_type` / `communication_language` / `primary_contact` / `contact` /
  `address` out of it).
- A history/audit trail of edits made after completion — the system already has no such trail for
  edits made before completion either; this doesn't add a new requirement for it.

## Anti-goals (would mean we got this wrong even if it "works")

- The in-progress peek feeling heavy — if it requires leaving the page or loses her place in the
  form, it has failed its purpose.
- A cryptic failure when the pedigree genuinely can't be rendered (PedigreeDraw unreachable, or a
  truly malformed document) — it must say plainly why, not surface a raw stack trace. (A
  sparse-but-valid diagram — e.g. only the proband answered so far, PedigreeDraw happily renders a
  near-blank single-node image for this — is fine as-is; see "Decisions already made" below.)
- "Editing after completion" silently requiring a second action to re-finish, or leaving the
  session stuck in a confusing state if she edits and then just closes the tab.

## Constraints

- PedigreeDraw's own architecture/behavior is out of scope to change — it is someone else's
  proprietary tool, used as-is over its documented HTTP contract (`POST /render`, `GET
  /healthz`/`/readyz`).
- No new frontend dependency for image handling — plain `fetch` + `Blob` + object URLs, consistent
  with this codebase's otherwise dependency-light frontend.
- No new backend dependency for the proxy call — `java.net.http.HttpClient` (JDK 11+, already
  available) rather than adding a REST client library for one call.
- Ports already in use in this dev setup: this backend's own default is `8080`
  (`application.yml`), but the team convention is to run it on `8081` via an explicit
  `--server.port=8081` override (no docker-compose exists in this repo; that convention exists
  simply so 8080 stays free), and the frontend dev server uses `5173`. PedigreeDraw's own default
  port is *also* `8080` (`ServerConfig.DEFAULT_PORT`) — one more reason it can't just run with
  defaults here. It must run on a different port in this dev setup (this design picks `8082`).
- The user does all git commits/pushes herself; nothing in the implementation plan commits or
  pushes.

## Decisions already made (by Safiya, in conversation)

1. **In-progress interaction:** a lightweight, dismissible peek — not a page navigation. (→ a
   modal/overlay.)
2. **Completion interaction:** a proper, dedicated page for the final diagram.
3. **Post-completion editing:** no separate "mark complete again" step. Editing resumes the
   session; reaching the end of the forward flow again is what re-completes it — exactly the
   existing mechanism, reused rather than reinvented (see below).
4. **Sparse pedigrees:** no pre-check before calling PedigreeDraw. A near-blank diagram (e.g. only
   the proband answered so far) renders as-is — accurate, just not much to look at yet. No extra
   guard logic anywhere in this design.
5. **Scope:** solo work on Safiya's machine for now. PedigreeDraw stays local to her `Downloads`
   folder; no provisioning path for Pranav is needed in this pass — revisit if/when he needs it.

## The parts of the how, decided

### 1. Backend: a proxy endpoint, not a passthrough schema

New `GET /api/v1/assessment/{sessionId}/pedigree-diagram?format=svg|png|pdf` (default `svg`) on
`PedigreeExportController`, backed by a new `PedigreeDiagramService`:

1. Calls the existing `PedigreeExportService.export(sessionId)` to get the JSON (unchanged).
2. POSTs it as the request body to `{pedigree.render.service.base-url}/render?format={format}`
   via `java.net.http.HttpClient`.
3. Returns the response bytes with the `Content-Type` PedigreeDraw reports
   (`image/svg+xml`, `image/png`, or `application/pdf`).

Error translation (PedigreeDraw's status → ours):

| PedigreeDraw | Our response | Why |
|---|---|---|
| `200` | `200` + bytes, same `Content-Type` | pass through |
| `422` (`application/problem+json`, invalid document) | `422` + a short message naming this is a pedigree-data issue, body passed through for debugging | Should be rare — our export is schema-complete even mid-assessment — but a genuinely empty/malformed case (e.g., a session with no relationship edges at all) should say so, not 500 |
| connection refused / timeout | `503 Service Unavailable`, `"Pedigree rendering service is not reachable"` | PedigreeDraw isn't running — an operational fact, not a data problem |
| any other non-2xx | `502 Bad Gateway` | unexpected upstream failure |

New config in `application.yml`:

```yaml
pedigree:
  render:
    service:
      base-url: http://localhost:8082
      timeout-ms: 10000
```

`8082` is this design's pick for PedigreeDraw's dev port (8080/8081/5173 are already taken).
Overridable via the usual Spring property mechanisms — no new custom env var plumbing needed.

### 2. Running PedigreeDraw locally (my call — operational, not a product decision)

Built once with `cd PedigreeDraw/pedigree-tool && mvn verify`, then run the same way the backend
and frontend dev servers already are — a long-running background process, started manually
alongside them:

```bash
cd PedigreeDraw/pedigree-tool
PEDIGREE_HTTP_PORT=8082 java -jar pedigree-http/target/pedigree-http-*-uber.jar
```

This design does not add process supervision, Docker, or auto-start-on-backend-boot — if that's
wanted later it's a separate, small follow-up. The implementation plan will document this command
plainly (e.g., in a README note) so it isn't a tribal-knowledge gap.

### 3. Frontend: one blob-fetching client function, two consumers

`patient-portal/src/api/client.js` gets `getPedigreeDiagram(sessionId, format = 'svg')`, using
`fetch` directly (not the shared JSON `request()` helper, since the response is binary) and
returning the parsed error body on a non-2xx response so callers can show the specific message
from the table above.

**Consumer 1 — `PedigreeViewerModal.jsx` (new).** The in-progress peek. Renders over the current
page as an overlay; fetches the SVG, shows it via an `<img>` bound to
`URL.createObjectURL(blob)` (never `dangerouslySetInnerHTML` — treating the response as an opaque
image, not markup, is the safer default even though we control the renderer). Has its own
loading / error / loaded states, and a close button; the object URL is revoked on close/unmount.
Triggered by a new "Pedigree Viewer" button.

**Consumer 2 — `CompletionPage.jsx` (reworked).** Today it already fetches `getPedigreeExport`
on mount purely to confirm the assessment is valid (with its own loading/error/retry state) before
showing a one-line "Thank you" — only that success-path render is actually new work. The rework
drops the separate `getPedigreeExport` check (my call — a diagram-render failure already implies
an export failure, so one fetch can drive both the content and the loading/error state) and fetches
the diagram itself as the main content (same blob/object-URL approach as the modal), plus:
- An **"Edit answers"** button/link to `/assessment/:sessionId` (the existing route) — see part 4.
- **Download** buttons for SVG/PNG/PDF (my call — PedigreeDraw already produces all three for
  free; each is just another `getPedigreeDiagram` call with a different `format`, piped into an
  `<a download>` click). Flag if you don't want this; easy to drop.

### 4. Where the button and the divider go (`TemplateView.jsx`)

The left column (today: just `VisitedTemplatesNav`, `sticky top-4 self-start`, with a `border-r`
that only runs as tall as the nav's own content) becomes a full-height flex column:

- The column itself switches from `self-start` to `self-stretch`, so it spans the same height as
  the question panel next to it — **this is what makes the divider line run the full page height**,
  independent of how long the visited-templates list is.
- Inside it, the nav keeps `sticky top-4` (unchanged behavior while scrolling).
- The new **"Pedigree Viewer" button** sits at the bottom of that same column, `sticky bottom-4`
  (so it's reachable without scrolling all the way down) — satisfying "bottom left" as the bottom
  of the left sidebar region, sticky like the nav above it. (My interpretation of "bottom left" —
  flag if you pictured a corner-of-the-viewport floating button instead; that's a quick change.)

### 5. Post-completion editing — reusing what already exists, not building anew

This is the most important finding: **the backend mechanism for this already exists and needs no
changes.** `AssessmentService.resumeAtTemplate(sessionId, templateId, familyMemberId)` already:

- Lets the proband jump back to any previously-visited template (via `requireVisited`, which also
  already allows zero-question templates like `FAMILY_MEMBER_HUB`, so she can add family members
  too, not just edit answers).
- Automatically flips the session from `COMPLETED` back to `IN_PROGRESS` the moment the **proband**
  resumes (a family member resuming into their own already-finished pass never touches the
  session-level status — correctly, since their pass was never what completed it).
- From there, the normal `submitAnswer` / `nextTemplate` endpoints work exactly as they do
  mid-assessment — `submitAnswer`'s own `requireInProgress` gate stops throwing once status is back
  to `IN_PROGRESS`, and `nextTemplate` never had a throwing gate at all, it just inline-checks
  status and short-circuits to a "completed" response when it's `COMPLETED` (which, again, it no
  longer is). Same net effect, two different mechanisms. This also means the existing FR-9
  cascade-invalidation of downstream answers that no longer apply after an edit keeps working
  unchanged.
- Walking `Next` forward all the way to the end **again** re-completes the session through the
  exact same code path as the first time — which is precisely "no separate step": finishing the
  walk IS the completion step, same as originally.

What's actually missing is purely on the frontend — **there is currently no way to reach this
mechanism from a completed session**, because `SessionListPage` routes a `COMPLETED` session
straight to `/completion/:id`, and even if you reached `/assessment/:id` directly,
`TemplateView` doesn't render anything useful for the all-null "completed" response shape it gets
back on first load. So the only two things to actually build:

- `CompletionPage`'s new **"Edit answers"** link → navigates to `/assessment/:sessionId` (added in
  part 3 above).
- `TemplateView.jsx` gets a new render branch: when the fetched template's `completed === true`
  on first load (as opposed to being told so after clicking `Next`, which already navigates away
  via `onCompleted`), show the visited-templates nav plus a plain message — *"This assessment is
  complete. Select a section to review or edit it."* — instead of today's actual behavior if you
  reach that URL directly: an empty-titled panel (no questions, since `templateName`/`templateCode`
  come back `null`) with a still-functional "Next" button that, on one click, correctly detects
  `completed: true` and navigates to the completion page — confusing rather than broken, but still
  worth replacing with the real message. Clicking any template in the nav calls the existing
  `resumeAtTemplate` and everything above takes over.

**Known, accepted side effect:** if she edits something after completion and then just closes the
tab without walking back to the end, the session sits at `IN_PROGRESS` in the session list until
she does. This mirrors exactly how resuming any other in-progress session already looks today —
not a new wrinkle this design introduces, just the existing status model applied here too.

## What's left to the builder

- Exact error-message copy for the three failure states in the peek modal and the completion page.
- The completion page's download buttons download immediately per format on click (not a
  format-choice affordance first) — simplest, least code.
- Test coverage shape for the new backend proxy (mocking the PedigreeDraw HTTP call) and the new
  frontend components — follow this codebase's existing testing conventions (`@SpringBootTest` /
  `@Transactional` integration style on the backend, Vitest + Testing Library on the frontend).
- Whether `PedigreeDiagramService` is its own class or a method added to the existing
  `PedigreeExportService` — builder's call based on what reads cleaner once written.
- The in-progress peek modal always fetches fresh on open rather than caching the last image —
  simplest, and the underlying data may have changed since it was last opened.
