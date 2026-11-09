# Pedigree Viewer & Post-Completion Editing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a user peek at the in-progress pedigree diagram during an assessment, see a proper final pedigree page once it's complete, and keep editing an assessment indefinitely after completion with no separate "reopen" step.

**Architecture:** The backend proxies pedigree rendering: it already builds the pedigree-export JSON (`PedigreeExportService`, unchanged); a new `PedigreeDiagramService` POSTs that JSON to PedigreeDraw's `/render` endpoint (a separate local Java HTTP service) and streams the image bytes back under our own API, so the browser never talks to PedigreeDraw directly (no CORS, one error-translation point). The frontend adds a lightweight modal for the in-progress peek, reworks the completion page into the real final-pedigree destination, and fixes `TemplateView` so a completed session is actually browsable/editable again — reusing `AssessmentService.resumeAtTemplate`, which already flips a completed session back to `IN_PROGRESS` and needs no backend change.

**Tech Stack:** Backend: Spring Boot 4.1, Java 21, `java.net.http.HttpClient` (JDK, no new dependency), Jackson 3 (`tools.jackson.*`), Lombok, `@SpringBootTest`/`@Transactional` integration tests. Frontend: Vite, React 19, Tailwind, Vitest + Testing Library, plain `fetch`/`Blob`/object URLs (no new dependency). External: PedigreeDraw (Java 21 Maven project at `C:\Users\nonst\Downloads\PedigreeDraw\PedigreeDraw`, run as a local sidecar process, out of scope to modify).

**Spec:** `docs/superpowers/specs/2026-11-02-pedigree-viewer-and-post-completion-editing-design.md`

## Global Constraints

- No new third-party dependency on either side for this feature (HTTP client and image handling both come from the platform/JDK already in use).
- PedigreeDraw's own code, schema, and behavior are out of scope to change — integrate over its documented `POST /render` contract only.
- PedigreeDraw must run on a port other than `8080` (its own default, and this backend's own `application.yml` default), `8081` (this backend's dev convention), or `5173` (frontend dev server) — this plan uses `8082`.
- No git commit or push at any step — stage with `git add` at most. The user commits and pushes herself.
- No session-status or `requireInProgress` backend change anywhere — `AssessmentService.resumeAtTemplate` already does everything post-completion editing needs (verified against the real code before this plan was written).
- No pre-check for a sparse/near-empty pedigree document — render whatever PedigreeDraw returns, even a near-blank single-node image.
- Follow TDD (superpowers:test-driven-development) for every task below.

## Review Focus

- **PedigreeDraw unreachable** (not started, wrong port): the peek modal and the completion page must each show a plain "the pedigree service isn't available" message, not a stuck spinner or a raw fetch error. Covered in Tasks 1, 4, 6.
- **A 422 from PedigreeDraw** (a pedigree document it considers invalid, however unlikely given our export is schema-complete): must surface as a clear, non-crashing message distinct from "unreachable" — not swallowed as a generic failure. Covered in Tasks 1, 2.
- **Opening/closing the peek modal repeatedly**: each open must fetch fresh (not show a stale diagram from a previous open) and each close must revoke its object URL (no leaked blob URLs building up over a long assessment session). Covered in Task 4.
- **Landing on `/assessment/:sessionId` for an already-completed session** (via the new "Edit answers" link, or directly): must show the visited-templates nav and a clear "this is complete, pick a section to edit" message, not today's confusing empty-titled panel. Covered in Task 5.
- **The family-member-pass-ended shape vs. the whole-session-completed shape**: both come back from the backend as `{completed: true, templateId: null}` — `TemplateView`'s new completed-branch must trigger only for the whole-session case (`familyMemberId == null`), never swallow the existing "this family member's own pass just ended" flow (which already returns early via `onFamilyMemberPassEnded` in `handleNext` and must keep doing so). Covered in Task 5.

---

## File Structure

Backend (`QP/src/main/java/com/questionnaire/questionnaire_platform/`):
- Create: `service/PedigreeDiagramService.java` — calls `PedigreeExportService`, POSTs to PedigreeDraw, returns a `PedigreeDiagram` record or throws a translated exception.
- Create: `exception/PedigreeServiceUnavailableException.java`, `exception/InvalidPedigreeDocumentException.java`, `exception/PedigreeRenderFailedException.java`.
- Modify: `exception/GlobalExceptionHandler.java` — add handlers for the three new exceptions (503, 422, 502).
- Modify: `controller/PedigreeExportController.java` — add the `/pedigree-diagram` endpoint.
- Modify: `src/main/resources/application.yml` — add `pedigree.render.service.base-url` / `.timeout-ms`.
- Test: `src/test/java/com/questionnaire/questionnaire_platform/service/PedigreeDiagramServiceTest.java`, `src/test/java/com/questionnaire/questionnaire_platform/controller/PedigreeDiagramIntegrationTest.java` (new file, or add to an existing `@SpringBootTest` covering `PedigreeExportController` if one already exists under that name — check before creating).

Frontend (`patient-portal/src/`):
- Modify: `api/client.js` — add `getPedigreeDiagram`.
- Create: `components/PedigreeViewerModal.jsx`, `components/PedigreeViewerModal.test.jsx`.
- Modify: `components/TemplateView.jsx`, `components/TemplateView.test.jsx` — layout change + completed-branch + the new button.
- Modify: `pages/CompletionPage.jsx`, `pages/CompletionPage.test.jsx` — becomes the final-pedigree page.

Operational (not source):
- PedigreeDraw itself, built and run as documented in the spec — Task 7.

---

### Task 1: `PedigreeDiagramService` — proxy call to PedigreeDraw

**Files:**
- Create: `QP/src/main/java/com/questionnaire/questionnaire_platform/service/PedigreeDiagramService.java`
- Create: `QP/src/main/java/com/questionnaire/questionnaire_platform/exception/PedigreeServiceUnavailableException.java`
- Create: `QP/src/main/java/com/questionnaire/questionnaire_platform/exception/InvalidPedigreeDocumentException.java`
- Create: `QP/src/main/java/com/questionnaire/questionnaire_platform/exception/PedigreeRenderFailedException.java`
- Test: `QP/src/test/java/com/questionnaire/questionnaire_platform/service/PedigreeDiagramServiceTest.java`

**Interfaces:**
- Consumes: `PedigreeExportService.export(UUID sessionId) -> JsonNode` (existing, unchanged); `JsonColumnMapper.write(JsonNode) -> String` (existing, unchanged).
- Produces: `public record PedigreeDiagram(byte[] bytes, String contentType)`; `public PedigreeDiagram render(UUID sessionId, String format)` on `PedigreeDiagramService`, throwing `PedigreeServiceUnavailableException` (connection failure/timeout), `InvalidPedigreeDocumentException` (upstream 422, message includes the upstream body), or `PedigreeRenderFailedException` (any other non-2xx, message includes the upstream status code). Task 2 calls this method and these exception types.

The three exception classes are plain `RuntimeException` subclasses with a `String message` constructor, matching the existing style of `InvalidConfigurationException`/`InvalidAnswerException` in the same package — no new fields needed.

- [ ] **Step 1: Write the failing test using a real, local fake HTTP server (no mocking library)**

Write this as a `@SpringBootTest` `@Transactional` integration test (consistent with every other service test in this codebase — see `AdminConfigurationIntegrationTest`), autowiring the real `PedigreeExportService` and seeding a minimal session the same way `StructuredQuestionIntegrationTest`/`TestFixtures` do elsewhere, so `export()` returns a real small document. Stand in for PedigreeDraw with `com.sun.net.httpserver.HttpServer` (JDK, already implicitly available — PedigreeDraw's own `pedigree-http` module is built on the same class) bound to `localhost:0` (an ephemeral port), started in a `@BeforeEach` and stopped in `@AfterEach`. Override `pedigree.render.service.base-url` to that fake server's `http://localhost:<port>` via `@DynamicPropertySource`, reading the port from a field the `@BeforeEach` sets before the property source runs (Spring evaluates `@DynamicPropertySource` lazily, so starting the server first is enough).

```java
@SpringBootTest
@Transactional
class PedigreeDiagramServiceTest {
    private static HttpServer fakeServer;

    @DynamicPropertySource
    static void overrideBaseUrl(DynamicPropertyRegistry registry) {
        // start fakeServer here (ephemeral port), register its address
    }

    @Test
    void rendersSvgBytesFromAMinimalSession() {
        // seed a session via TestFixtures, same pattern as PedigreeExportServiceTest
        // fakeServer responds 200, Content-Type: image/svg+xml, body "<svg>fake</svg>"
        PedigreeDiagram result = pedigreeDiagramService.render(sessionId, "svg");
        assertThat(result.contentType()).isEqualTo("image/svg+xml");
        assertThat(new String(result.bytes())).isEqualTo("<svg>fake</svg>");
    }

    @Test
    void throwsPedigreeServiceUnavailableWhenNothingIsListening() {
        // override base-url to a port nothing listens on (e.g. the fakeServer's port after stopping it)
        assertThatThrownBy(() -> pedigreeDiagramService.render(sessionId, "svg"))
                .isInstanceOf(PedigreeServiceUnavailableException.class);
    }

    @Test
    void throwsInvalidPedigreeDocumentOn422() {
        // fakeServer responds 422 with a problem+json body
        assertThatThrownBy(() -> pedigreeDiagramService.render(sessionId, "svg"))
                .isInstanceOf(InvalidPedigreeDocumentException.class);
    }

    @Test
    void throwsPedigreeRenderFailedOnOtherNon2xx() {
        // fakeServer responds 500
        assertThatThrownBy(() -> pedigreeDiagramService.render(sessionId, "svg"))
                .isInstanceOf(PedigreeRenderFailedException.class);
    }
}
```

Look at `PedigreeExportServiceTest.java` and `TestFixtures.java` first for the exact seeding helper to reuse for "a minimal session that exports cleanly" — don't reinvent it.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd QP && mvn test -Dtest=PedigreeDiagramServiceTest`
Expected: FAIL — `PedigreeDiagramService`/`PedigreeDiagram`/the three exceptions don't exist yet.

- [ ] **Step 3: Implement the three exception classes and `PedigreeDiagramService`**

Each exception: `public class XxxException extends RuntimeException { public XxxException(String message) { super(message); } }`.

`PedigreeDiagramService` is a `@Service` with an explicit constructor (not `@RequiredArgsConstructor`, so `@Value` can be applied to the two config parameters):

```java
public PedigreeDiagramService(
        PedigreeExportService pedigreeExportService,
        JsonColumnMapper jsonColumnMapper,
        @Value("${pedigree.render.service.base-url}") String baseUrl,
        @Value("${pedigree.render.service.timeout-ms}") long timeoutMs)
```

It builds one `HttpClient` in the constructor (`HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeoutMs)).build()`) and reuses it across calls. `render(UUID sessionId, String format)`: call `pedigreeExportService.export(sessionId)`, serialize with `jsonColumnMapper.write(...)`, `POST` to `baseUrl + "/render?format=" + format` with `Content-Type: application/json`, using `HttpResponse.BodyHandlers.ofByteArray()`. Catch `IOException`/`InterruptedException` from `httpClient.send(...)` and rethrow as `PedigreeServiceUnavailableException`. On success read `Content-Type` from `response.headers().firstValue("Content-Type")` (fall back to `"application/octet-stream"` if absent). Map `response.statusCode()`: `200` → return `PedigreeDiagram`; `422` → `InvalidPedigreeDocumentException` (message includes `new String(response.body(), StandardCharsets.UTF_8)`); anything else → `PedigreeRenderFailedException` (message includes the status code).

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd QP && mvn test -Dtest=PedigreeDiagramServiceTest`
Expected: PASS, 4/4.

- [ ] **Step 5: Stage**

```bash
git add QP/src/main/java/com/questionnaire/questionnaire_platform/service/PedigreeDiagramService.java \
        QP/src/main/java/com/questionnaire/questionnaire_platform/exception/PedigreeServiceUnavailableException.java \
        QP/src/main/java/com/questionnaire/questionnaire_platform/exception/InvalidPedigreeDocumentException.java \
        QP/src/main/java/com/questionnaire/questionnaire_platform/exception/PedigreeRenderFailedException.java \
        QP/src/test/java/com/questionnaire/questionnaire_platform/service/PedigreeDiagramServiceTest.java
```

---

### Task 2: `/pedigree-diagram` endpoint, error mapping, and config

**Files:**
- Modify: `QP/src/main/java/com/questionnaire/questionnaire_platform/controller/PedigreeExportController.java`
- Modify: `QP/src/main/java/com/questionnaire/questionnaire_platform/exception/GlobalExceptionHandler.java`
- Modify: `QP/src/main/resources/application.yml`
- Test: `QP/src/test/java/com/questionnaire/questionnaire_platform/controller/PedigreeDiagramIntegrationTest.java` (new)

**Interfaces:**
- Consumes: Task 1's `PedigreeDiagramService.render(UUID, String) -> PedigreeDiagram` and its three exceptions.
- Produces: `GET /api/v1/assessment/{sessionId}/pedigree-diagram?format=svg|png|pdf` (format optional, default `svg`), `200` with the image bytes and matching `Content-Type` on success, `422`/`503`/`502` with the existing `ApiError` JSON shape on the three failure cases. This is what Task 3's frontend client calls.

- [ ] **Step 1: Add config to `application.yml`**

```yaml
pedigree:
  render:
    service:
      base-url: http://localhost:8082
      timeout-ms: 10000
```

- [ ] **Step 2: Write the failing integration test**

```java
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PedigreeDiagramIntegrationTest {
    @Test
    void returnsSvgBytesWithContentTypeFromTheRenderService() throws Exception {
        // seed a minimal session (same helper as Task 1), stub PedigreeDiagramService's
        // downstream via the same fake-HttpServer-on-ephemeral-port + @DynamicPropertySource
        // approach as Task 1, or inject a fake PedigreeDiagramService bean with @MockBean/@MockitoBean
        // if Mockito is confirmed available in this project's test scope - check an existing test
        // for @MockBean usage first; if none exists, reuse Task 1's fake-HTTP-server approach here too.
        mockMvc.perform(get("/api/v1/assessment/{id}/pedigree-diagram", sessionId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/svg+xml"));
    }

    @Test
    void returns503WhenPedigreeDrawIsUnreachable() throws Exception {
        mockMvc.perform(get("/api/v1/assessment/{id}/pedigree-diagram", sessionId))
                .andExpect(status().isServiceUnavailable());
    }
}
```

This codebase has no existing `@MockBean`/`@MockitoBean` usage at the controller/Spring-context level (only plain Mockito inside two unit tests for answer writers) — so don't introduce that pattern here either. Reuse Task 1's exact fake-`HttpServer`-on-an-ephemeral-port + `@DynamicPropertySource` approach, standing up one server per test class the same way.

- [ ] **Step 3: Run to verify it fails**

Run: `cd QP && mvn test -Dtest=PedigreeDiagramIntegrationTest`
Expected: FAIL — endpoint doesn't exist (404).

- [ ] **Step 4: Add the endpoint and the exception handlers**

In `PedigreeExportController`, inject `PedigreeDiagramService` and add:

```java
@GetMapping("/pedigree-diagram")
public ResponseEntity<byte[]> diagram(@PathVariable UUID sessionId,
                                       @RequestParam(defaultValue = "svg") String format) {
    PedigreeDiagram diagram = pedigreeDiagramService.render(sessionId, format);
    return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_TYPE, diagram.contentType())
            .body(diagram.bytes());
}
```

In `GlobalExceptionHandler`, three new handlers following the file's existing `build(status, message, request)` pattern: `PedigreeServiceUnavailableException` → `HttpStatus.SERVICE_UNAVAILABLE`; `InvalidPedigreeDocumentException` → `HttpStatus.UNPROCESSABLE_ENTITY`; `PedigreeRenderFailedException` → `HttpStatus.BAD_GATEWAY`.

- [ ] **Step 5: Run to verify it passes**

Run: `cd QP && mvn test -Dtest=PedigreeDiagramIntegrationTest`
Expected: PASS, 2/2 (or however many tests Step 2 ended up with).

- [ ] **Step 6: Run the full backend suite**

Run: `cd QP && mvn test`
Expected: all pass, no regressions.

- [ ] **Step 7: Stage**

```bash
git add QP/src/main/java/com/questionnaire/questionnaire_platform/controller/PedigreeExportController.java \
        QP/src/main/java/com/questionnaire/questionnaire_platform/exception/GlobalExceptionHandler.java \
        QP/src/main/resources/application.yml \
        QP/src/test/java/com/questionnaire/questionnaire_platform/controller/PedigreeDiagramIntegrationTest.java
```

---

### Task 3: Frontend API client — `getPedigreeDiagram`

**Files:**
- Modify: `patient-portal/src/api/client.js`
- Test: `patient-portal/src/api/client.test.js`

**Interfaces:**
- Consumes: `GET /api/v1/assessment/{sessionId}/pedigree-diagram?format=` from Task 2.
- Produces: `export const getPedigreeDiagram = (sessionId, format = 'svg') => Promise<Blob>`, rejecting with `ApiError` (existing class, already in this file) on a non-2xx response. Task 4 and Task 6 both call this.

- [ ] **Step 1: Write the failing test**

Look at `client.test.js`'s existing tests for `getSessions`/`submitAnswer` first to match its mocking style (likely `vi.spyOn(global, 'fetch')` or an MSW-less manual fetch mock — use whatever's already there).

```js
it('getPedigreeDiagram resolves with the response blob on success', async () => {
  // mock fetch to resolve { ok: true, blob: () => Promise.resolve(new Blob(['<svg/>'])) }
  const blob = await getPedigreeDiagram('s1', 'svg');
  expect(blob).toBeInstanceOf(Blob);
  // assert fetch was called with .../assessment/s1/pedigree-diagram?format=svg
});

it('getPedigreeDiagram rejects with an ApiError built from the JSON error body on failure', async () => {
  // mock fetch to resolve { ok: false, status: 503, json: () => Promise.resolve({ message: 'Pedigree rendering service is not reachable' }) }
  await expect(getPedigreeDiagram('s1')).rejects.toMatchObject({ status: 503, message: 'Pedigree rendering service is not reachable' });
});
```

- [ ] **Step 2: Run to verify it fails**

Run: `cd patient-portal && npx vitest run src/api/client.test.js`
Expected: FAIL — `getPedigreeDiagram` is not exported.

- [ ] **Step 3: Implement `getPedigreeDiagram`**

Does not go through the shared `request()` helper (that one always calls `res.json()`, which would fail on binary image bytes). New standalone function in the same file:

```js
export const getPedigreeDiagram = async (sessionId, format = 'svg') => {
  const res = await fetch(`${BASE_URL}/assessment/${sessionId}/pedigree-diagram?format=${format}`);
  if (!res.ok) {
    const data = await res.json().catch(() => null);
    throw new ApiError(res.status, data?.message ?? 'Request failed');
  }
  return res.blob();
};
```

- [ ] **Step 4: Run to verify it passes**

Run: `cd patient-portal && npx vitest run src/api/client.test.js`
Expected: PASS.

- [ ] **Step 5: Stage**

```bash
git add patient-portal/src/api/client.js patient-portal/src/api/client.test.js
```

---

### Task 4: `PedigreeViewerModal` — the in-progress peek

**Files:**
- Create: `patient-portal/src/components/PedigreeViewerModal.jsx`
- Test: `patient-portal/src/components/PedigreeViewerModal.test.jsx`

**Interfaces:**
- Consumes: Task 3's `getPedigreeDiagram(sessionId, format)`.
- Produces: `export default function PedigreeViewerModal({ sessionId, onClose })` — a self-fetching overlay. Task 5 renders this conditionally and passes these two props.

- [ ] **Step 1: Write the failing tests**

```jsx
it('fetches the diagram on mount and shows it as an image once loaded', async () => {
  vi.spyOn(client, 'getPedigreeDiagram').mockResolvedValue(new Blob(['<svg/>'], { type: 'image/svg+xml' }));
  render(<PedigreeViewerModal sessionId="s1" onClose={vi.fn()} />);
  expect(await screen.findByRole('img')).toBeInTheDocument();
});

it('shows a loading state before the diagram resolves', () => {
  vi.spyOn(client, 'getPedigreeDiagram').mockReturnValue(new Promise(() => {}));
  render(<PedigreeViewerModal sessionId="s1" onClose={vi.fn()} />);
  expect(screen.getByText(/loading/i)).toBeInTheDocument();
});

it('shows the error message when the service is unavailable', async () => {
  vi.spyOn(client, 'getPedigreeDiagram').mockRejectedValue(new client.ApiError(503, 'Pedigree rendering service is not reachable'));
  render(<PedigreeViewerModal sessionId="s1" onClose={vi.fn()} />);
  expect(await screen.findByText('Pedigree rendering service is not reachable')).toBeInTheDocument();
});

it('calls onClose when the close button is clicked', async () => {
  vi.spyOn(client, 'getPedigreeDiagram').mockResolvedValue(new Blob(['<svg/>'], { type: 'image/svg+xml' }));
  const onClose = vi.fn();
  render(<PedigreeViewerModal sessionId="s1" onClose={onClose} />);
  await screen.findByRole('img');
  fireEvent.click(screen.getByRole('button', { name: /close/i }));
  expect(onClose).toHaveBeenCalled();
});
```

`URL.createObjectURL`/`URL.revokeObjectURL` aren't implemented in jsdom by default — check `test-setup.js` first; if neither is stubbed there already, add a minimal `vi.stubGlobal` for both at the top of this test file (`createObjectURL` returning a fixed string, `revokeObjectURL` a no-op `vi.fn()`).

- [ ] **Step 2: Run to verify they fail**

Run: `cd patient-portal && npx vitest run src/components/PedigreeViewerModal.test.jsx`
Expected: FAIL — module doesn't exist.

- [ ] **Step 3: Implement `PedigreeViewerModal`**

`useEffect` on mount: call `getPedigreeDiagram(sessionId)`, on resolve `URL.createObjectURL(blob)` into state, on reject store the `ApiError`'s `.message`. Revoke the created object URL in the effect's cleanup function (so repeated opens never leak one). Render: a fixed-position overlay (`fixed inset-0 bg-black/50 flex items-center justify-center`, `role="dialog"` `aria-modal="true"`) containing a panel with a "Close" button (`onClick={onClose}`) and, depending on state, "Loading..." text, the error message, or `<img src={objectUrl} alt="Pedigree diagram" />`.

- [ ] **Step 4: Run to verify they pass**

Run: `cd patient-portal && npx vitest run src/components/PedigreeViewerModal.test.jsx`
Expected: PASS, 4/4.

- [ ] **Step 5: Stage**

```bash
git add patient-portal/src/components/PedigreeViewerModal.jsx patient-portal/src/components/PedigreeViewerModal.test.jsx
```

---

### Task 5: `TemplateView.jsx` — full-height divider, bottom button, completed-browse branch

**Files:**
- Modify: `patient-portal/src/components/TemplateView.jsx`
- Modify: `patient-portal/src/components/TemplateView.test.jsx`

**Interfaces:**
- Consumes: Task 4's `PedigreeViewerModal`.
- Produces: no new exports — this task only changes `TemplateView`'s own rendering and layout.

- [ ] **Step 1: Write the failing tests**

```jsx
it('shows a Pedigree Viewer button that opens the modal, and closing it removes the modal', async () => {
  vi.spyOn(client, 'getCurrentTemplate').mockResolvedValue({ templateCode: 'DEMOGRAPHICS', completed: false, familyMemberId: null, questions: [] });
  render(<TemplateView sessionId="s1" familyMemberId={null} subjectLabel="you" onCompleted={vi.fn()} onFamilyMemberPassEnded={vi.fn()} />);

  fireEvent.click(await screen.findByRole('button', { name: /pedigree viewer/i }));
  expect(screen.getByRole('dialog')).toBeInTheDocument();

  // the modal's own content/behavior is covered by PedigreeViewerModal.test.jsx; this only
  // confirms TemplateView wires the button up and mounts/unmounts the modal correctly.
});

it('shows a browse message and the visited-templates nav instead of a blank panel when the session is already completed', async () => {
  vi.spyOn(client, 'getCurrentTemplate').mockResolvedValue({ templateCode: null, templateId: null, completed: true, familyMemberId: null, questions: [] });
  vi.spyOn(client, 'getVisitedTemplates').mockResolvedValue([{ templateId: 't1', templateCode: 'DEMOGRAPHICS', templateName: 'Demographics' }]);

  render(<TemplateView sessionId="s1" familyMemberId={null} subjectLabel="you" onCompleted={vi.fn()} onFamilyMemberPassEnded={vi.fn()} />);

  expect(await screen.findByText(/this assessment is complete/i)).toBeInTheDocument();
  expect(screen.getByText('Demographics')).toBeInTheDocument(); // the nav still renders
  expect(screen.queryByRole('button', { name: 'Next' })).not.toBeInTheDocument();
});
```

- [ ] **Step 2: Run to verify they fail**

Run: `cd patient-portal && npx vitest run src/components/TemplateView.test.jsx`
Expected: FAIL — no "Pedigree Viewer" button yet, no completed-branch message yet.

- [ ] **Step 3: Implement**

Add `showPedigreeViewer` state (`useState(false)`). Add a button (`onClick={() => setShowPedigreeViewer(true)}`, text "Pedigree Viewer") at the bottom of the left sidebar column, and render `{showPedigreeViewer && <PedigreeViewerModal sessionId={sessionId} onClose={() => setShowPedigreeViewer(false)} />}`.

Layout: change the sidebar wrapper div's classes from `md:w-56 md:shrink-0 md:sticky md:top-4 md:self-start md:border-r md:border-gray-200 md:pr-6` to `md:w-56 md:shrink-0 md:self-stretch md:border-r md:border-gray-200 md:pr-6 md:flex md:flex-col` (drops `sticky`/`self-start` from the outer div — the divider now spans the full row height because the column stretches to match its sibling). Inside it, wrap `VisitedTemplatesNav` in its own `md:sticky md:top-4` div (preserves today's "nav stays put while scrolling" behavior), and put the new button in a `md:sticky md:bottom-4 md:mt-auto` wrapper after it (pins it to the bottom of the viewport within this column, pushed down by `mt-auto` when the column is taller than its content).

Add the completed-browse branch: where `TemplateView` currently renders `{template.templateCode === 'FAMILY_MEMBER_HUB' ? (...) : (...)}`, add a first condition: `template.completed ? (<p>This assessment is complete. Select a section on the left to review or edit it.</p>) : template.templateCode === 'FAMILY_MEMBER_HUB' ? (...) : (...)`. Also suppress the generic bottom "Next" button when `template.completed` (it already hides for `FAMILY_MEMBER_HUB`; extend that same condition to `template.completed`).

- [ ] **Step 4: Run to verify they pass**

Run: `cd patient-portal && npx vitest run src/components/TemplateView.test.jsx`
Expected: PASS, all tests including every pre-existing one in this file.

- [ ] **Step 5: Run the full frontend suite**

Run: `cd patient-portal && npx vitest run`
Expected: all pass, no regressions (watch specifically for the existing `FAMILY_MEMBER_HUB` and `onFamilyMemberPassEnded` tests — they must still pass unchanged, per this plan's Review Focus item about not swallowing that flow).

- [ ] **Step 6: Stage**

```bash
git add patient-portal/src/components/TemplateView.jsx patient-portal/src/components/TemplateView.test.jsx
```

---

### Task 6: `CompletionPage.jsx` — the final pedigree page

**Files:**
- Modify: `patient-portal/src/pages/CompletionPage.jsx`
- Modify: `patient-portal/src/pages/CompletionPage.test.jsx`

**Interfaces:**
- Consumes: Task 3's `getPedigreeDiagram(sessionId, format)`. Navigates to the existing `/assessment/:sessionId` route (no new route).
- Produces: no new exports.

- [ ] **Step 1: Write the failing tests**

Read the existing `CompletionPage.test.jsx` first — it currently tests the `getPedigreeExport`-based loading/error/retry states; those tests get replaced (the export-validation call is removed per the design) rather than added to.

```jsx
it('shows the rendered diagram once it loads', async () => {
  vi.spyOn(client, 'getPedigreeDiagram').mockResolvedValue(new Blob(['<svg/>'], { type: 'image/svg+xml' }));
  render(<CompletionPage sessionId="s1" />);
  expect(await screen.findByRole('img')).toBeInTheDocument();
});

it('shows an error message when the diagram cannot be rendered', async () => {
  vi.spyOn(client, 'getPedigreeDiagram').mockRejectedValue(new client.ApiError(503, 'Pedigree rendering service is not reachable'));
  render(<CompletionPage sessionId="s1" />);
  expect(await screen.findByText('Pedigree rendering service is not reachable')).toBeInTheDocument();
});

it('has an Edit answers link back into the assessment', async () => {
  vi.spyOn(client, 'getPedigreeDiagram').mockResolvedValue(new Blob(['<svg/>'], { type: 'image/svg+xml' }));
  render(<MemoryRouter><CompletionPage sessionId="s1" /></MemoryRouter>);
  await screen.findByRole('img');
  expect(screen.getByRole('link', { name: /edit answers/i })).toHaveAttribute('href', '/assessment/s1');
});

it('offers SVG, PNG, and PDF downloads', async () => {
  vi.spyOn(client, 'getPedigreeDiagram').mockResolvedValue(new Blob(['<svg/>'], { type: 'image/svg+xml' }));
  render(<CompletionPage sessionId="s1" />);
  await screen.findByRole('img');
  expect(screen.getByRole('button', { name: /svg/i })).toBeInTheDocument();
  expect(screen.getByRole('button', { name: /png/i })).toBeInTheDocument();
  expect(screen.getByRole('button', { name: /pdf/i })).toBeInTheDocument();
});
```

Check whether this codebase already wraps page-level tests in `MemoryRouter` elsewhere (e.g. `SessionListPage.test.jsx`, since it also uses `useNavigate`) and match that pattern for the "Edit answers" link test — `CompletionPage` doesn't currently use react-router, so this is new for this file.

- [ ] **Step 2: Run to verify they fail**

Run: `cd patient-portal && npx vitest run src/pages/CompletionPage.test.jsx`
Expected: FAIL.

- [ ] **Step 3: Implement**

Replace the `getPedigreeExport`-based `check()`/`status` logic with a `useEffect` that calls `getPedigreeDiagram(sessionId)`, storing an object URL on success (revoked on unmount/re-fetch, same pattern as Task 4) or the error message on failure. Render: the diagram `<img>` (or the loading/error text), a `<Link to={`/assessment/${sessionId}`}>Edit answers</Link>` (import `Link` from `react-router-dom`, already a dependency per `App.jsx`), and three buttons ("SVG", "PNG", "PDF") that each call `getPedigreeDiagram(sessionId, format)` and trigger a download via a programmatically-clicked `<a download={`pedigree.${format}`} href={objectUrl}>` (create the anchor, click it, then revoke the object URL and remove the anchor).

- [ ] **Step 4: Run to verify they pass**

Run: `cd patient-portal && npx vitest run src/pages/CompletionPage.test.jsx`
Expected: PASS.

- [ ] **Step 5: Run the full frontend suite**

Run: `cd patient-portal && npx vitest run`
Expected: all pass. Also run `npm run lint` — expect only the pre-existing accepted `react(only-export-components)` warnings, nothing new.

- [ ] **Step 6: Stage**

```bash
git add patient-portal/src/pages/CompletionPage.jsx patient-portal/src/pages/CompletionPage.test.jsx
```

---

### Task 7: Build and run PedigreeDraw locally

**Files:** none (operational step, no source in this repo).

**Interfaces:**
- Produces: a running `pedigree-http` instance on `http://localhost:8082`, which Task 8 depends on.

- [ ] **Step 1: Build it**

Run: `cd "C:\Users\nonst\Downloads\PedigreeDraw\PedigreeDraw\pedigree-tool" && mvn verify`
Expected: `BUILD SUCCESS`, and `pedigree-http/target/pedigree-http-*-uber.jar` exists (`ls pedigree-http/target/*-uber.jar`).

- [ ] **Step 2: Start it on port 8082, in the background**

Run (from the same `pedigree-tool` directory, so its `config/`/`schema/` are found): `PEDIGREE_HTTP_PORT=8082 java -jar pedigree-http/target/pedigree-http-*-uber.jar` (spell the exact version out on Windows per its own README if the glob doesn't expand).
Expected log line: `Listening on port 8082 with N render threads ...`.

- [ ] **Step 3: Verify it answers**

Run: `curl -s -o /dev/null -w "%{http_code}" http://localhost:8082/healthz`
Expected: `200`.

Run: `curl -s -X POST --data-binary @"C:\Users\nonst\Downloads\PedigreeDraw\PedigreeDraw\samples\minimal.json" -H "Content-Type: application/json" http://localhost:8082/render -o /tmp/pedigree-smoke-test.svg` then check the file is non-empty and starts with `<svg`.
Expected: a valid SVG file written.

No commit step — nothing in this repo changed.

---

### Task 8: End-to-end verification against the real running stack

**Files:** none.

**Interfaces:**
- Consumes: everything from Tasks 1–7.

- [ ] **Step 1: Start all three processes**

Backend: `cd QP && ./mvnw.cmd spring-boot:run -Dspring-boot.run.arguments=--server.port=8081` (background). Frontend: `cd patient-portal && npm run dev` (background, port 5173). PedigreeDraw: as started in Task 7 (confirm it's still running, or restart it).

- [ ] **Step 2: Walk a real session through the peek**

Via `curl` against the real backend (mirroring how the 2026-11-01 plan's final task verified structured questions): start a session, answer enough of `DEMOGRAPHICS` to have a real individual, then `curl http://localhost:8081/api/v1/assessment/{sessionId}/pedigree-diagram` and confirm `200` with an `image/svg+xml` body that starts with `<svg`.

Expected: a real SVG comes back end-to-end through the full proxy chain (export → `PedigreeDiagramService` → PedigreeDraw → back).

- [ ] **Step 3: Walk the completion → edit → re-complete loop**

Drive the same session to completion via `POST /next-template` calls until `completed: true` comes back. Confirm `GET /current-template` for that session now short-circuits to the completed shape. Call `POST /templates/{anEarlierTemplateId}/resume-here` and confirm the response is a real template (not the completed placeholder) and that a subsequent `GET /assessment/sessions` entry for this session shows `IN_PROGRESS` again. Walk `POST /next-template` forward again to the end and confirm it reaches `completed: true` a second time with no extra calls needed — this is the "no separate step" requirement from the spec, verified against the real server.

Expected: every step matches the description above; no manual "mark complete" call exists or is needed.

- [ ] **Step 4: Stop the background processes**

Stop the backend, frontend, and PedigreeDraw processes started in Step 1 (and Task 7).

No commit step.
