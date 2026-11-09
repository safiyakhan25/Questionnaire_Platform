# Patient Portal Phase 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a React patient portal with minimal account login, wired to the existing QP backend, that renders the current question bank fully dynamically (new admin-added questions show up with no frontend change), supports family-member pass-switching, and verifies the pedigree export at completion.

**Architecture:** A small, additive backend auth module (`AppUser`/`AuthToken`, BCrypt-hashed passwords, opaque bearer tokens) sits beside the existing assessment API without modifying it — the frontend learns which `sessionId` belongs to the logged-in user and then talks to the existing `/api/v1/assessment/**` endpoints exactly as they are today. The frontend is a Vite + React SPA whose core piece is a dynamic question renderer driven entirely by each question's `type`/`options`/`validation`/`visibilityRule` from the API — no question content is ever hardcoded.

**Tech Stack:** Spring Boot 4.1 / Java 21 / Liquibase / Postgres (backend, existing); React 18 + Vite + React Router + Tailwind CSS (frontend, new); Vitest + React Testing Library (frontend tests); JUnit 5 + MockMvc (backend tests, existing conventions).

**Spec:** `docs/superpowers/specs/2026-10-24-patient-portal-phase1-design.md`

## Global Constraints

- 2-day timeline — tasks are kept small and sequential; no gold-plating beyond what the spec asks for.
- Existing `/api/v1/assessment/**`, `/api/v1/assessment/{sessionId}/family-members/**`, and `/api/v1/assessment/{sessionId}/pedigree-export` endpoints are used **unmodified** — no task may change their controllers, services, or DTOs.
- Passwords are hashed with BCrypt via the `spring-security-crypto` dependency only — not `spring-boot-starter-security`.
- The auth token is a plain opaque random string stored server-side in `AUTH_TOKEN`, not a JWT.
- `RelationshipType` and `MaternalPaternalSideType` option lists are hardcoded in the frontend; every question's type, options, validation, and visibility rule is always read from the API at runtime, never hardcoded.
- The app must be usable on both desktop and mobile viewports.

## Review Focus

- Signing up with an email that's already registered must be rejected with a clear error, not silently overwritten or a raw 500 — covered in Task 2.
- Logging in with a correct email but wrong password must fail without revealing whether the email exists at all — covered in Task 2.
- A required question left blank must block "Next" and name which fields are missing, using the backend's own error message — covered in Task 8.
- Refreshing the browser mid-assessment must resume the existing session, never silently start a second one — covered in Task 6.
- A `CHECKBOX` question must submit its answer as a JSON array even when only one option is checked, since the backend's validator requires array type for that question type — covered in Task 7.

---

## Task 1: Auth data model (`AppUser`/`AuthToken`)

**Files:**
- Create: `QP/src/main/resources/db/changelog/changes/018-create-app-user-and-auth-token.yaml`
- Modify: `QP/src/main/resources/db/changelog/db.changelog-master.yaml`
- Create: `QP/src/main/java/com/questionnaire/questionnaire_platform/entity/AppUser.java`
- Create: `QP/src/main/java/com/questionnaire/questionnaire_platform/entity/AuthToken.java`
- Create: `QP/src/main/java/com/questionnaire/questionnaire_platform/repository/AppUserRepository.java`
- Create: `QP/src/main/java/com/questionnaire/questionnaire_platform/repository/AuthTokenRepository.java`
- Modify: `QP/pom.xml`
- Test: `QP/src/test/java/com/questionnaire/questionnaire_platform/repository/AppUserRepositoryTest.java`

**Interfaces:**
- Produces: `AppUser` fields — `id: UUID`, `email: String`, `phone: String` (nullable), `passwordHash: String`, `sessionId: UUID` (nullable), `createdAt: LocalDateTime`. Getters/setters via Lombok `@Getter @Setter`, matching this codebase's existing entity style.
- Produces: `AuthToken` fields — `token: String` (primary key, not generated — the service sets it), `appUser: AppUser` (`@ManyToOne`), `createdAt: LocalDateTime`.
- Produces: `AppUserRepository.findByEmail(String email): Optional<AppUser>`, `AppUserRepository.existsByEmail(String email): boolean`.
- Produces: `AuthTokenRepository.findByToken(String token): Optional<AuthToken>`.

- [ ] **Step 1: Add the `spring-security-crypto` dependency to `QP/pom.xml`**

Add inside `<dependencies>`, no version tag needed (Spring Boot's dependency management covers it):
```xml
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-crypto</artifactId>
</dependency>
```

- [ ] **Step 2: Write the Liquibase changeset**

Create `018-create-app-user-and-auth-token.yaml` with two tables, following the exact column style already used in `006-create-person-table.yaml` (UUID PK via `gen_random_uuid()`, `TIMESTAMP` with `defaultValueComputed: now()` for `created_at`):
- `app_user`: `id UUID PK`, `email VARCHAR NOT NULL UNIQUE`, `phone VARCHAR` (nullable), `password_hash VARCHAR NOT NULL`, `session_id UUID` (nullable, no FK — it references a row in a different bounded context and must not couple this table to `questionnaire_session`), `created_at TIMESTAMP NOT NULL`.
- `auth_token`: `token VARCHAR PK` (not auto-generated — the application supplies it), `app_user_id UUID NOT NULL FK -> app_user(id)`, `created_at TIMESTAMP NOT NULL`.

Add the `- include:` entry for this file at the end of `db.changelog-master.yaml`, same pattern as every prior entry.

- [ ] **Step 3: Create `AppUser.java` and `AuthToken.java`** with the fields listed in Interfaces, following the existing entity style (`@Entity @Table(name = "...") @Getter @Setter @NoArgsConstructor`, `@Id @GeneratedValue(strategy = GenerationType.UUID)` for `AppUser.id` — `AuthToken.token` is `@Id` but **not** `@GeneratedValue`, since the service assigns it explicitly).

- [ ] **Step 4: Create `AppUserRepository.java` and `AuthTokenRepository.java`** with the methods listed in Interfaces, extending `JpaRepository` exactly as every other repository in this project does.

- [ ] **Step 5: Write the failing test**

```java
@DataJpaTest
class AppUserRepositoryTest {
    @Autowired private AppUserRepository repository;

    @Test
    void findByEmailAndExistsByEmailWorkAfterSaving() {
        AppUser user = new AppUser();
        user.setEmail("patient@example.com");
        user.setPasswordHash("hashed");
        user.setCreatedAt(LocalDateTime.now());
        repository.save(user);

        assertThat(repository.existsByEmail("patient@example.com")).isTrue();
        assertThat(repository.findByEmail("patient@example.com")).isPresent();
        assertThat(repository.existsByEmail("nobody@example.com")).isFalse();
    }
}
```

- [ ] **Step 6: Run test to verify it fails**

Run: `./mvnw.cmd test -Dtest=AppUserRepositoryTest`
Expected: FAIL (entity/repository/migration don't exist yet, or the test DB schema is out of date)

- [ ] **Step 7: Run test to verify it passes** (after Steps 1-4 are in place)

Run: `./mvnw.cmd test -Dtest=AppUserRepositoryTest`
Expected: PASS, and Liquibase log shows changeset `018-create-app-user-and-auth-token` applying cleanly to the test database.

- [ ] **Step 8: Commit**

```bash
git add QP/pom.xml QP/src/main/resources/db/changelog QP/src/main/java/com/questionnaire/questionnaire_platform/entity/AppUser.java QP/src/main/java/com/questionnaire/questionnaire_platform/entity/AuthToken.java QP/src/main/java/com/questionnaire/questionnaire_platform/repository/AppUserRepository.java QP/src/main/java/com/questionnaire/questionnaire_platform/repository/AuthTokenRepository.java QP/src/test/java/com/questionnaire/questionnaire_platform/repository/AppUserRepositoryTest.java
git commit -m "feat: add AppUser/AuthToken data model for patient portal auth"
```

---

## Task 2: AuthService (signup, login, token resolution, session linking)

**Files:**
- Create: `QP/src/main/java/com/questionnaire/questionnaire_platform/dto/SignupRequest.java`
- Create: `QP/src/main/java/com/questionnaire/questionnaire_platform/dto/LoginRequest.java`
- Create: `QP/src/main/java/com/questionnaire/questionnaire_platform/dto/UpdateSessionRequest.java`
- Create: `QP/src/main/java/com/questionnaire/questionnaire_platform/dto/AuthResponse.java`
- Create: `QP/src/main/java/com/questionnaire/questionnaire_platform/exception/InvalidCredentialsException.java`
- Create: `QP/src/main/java/com/questionnaire/questionnaire_platform/service/AuthService.java`
- Test: `QP/src/test/java/com/questionnaire/questionnaire_platform/service/AuthServiceTest.java`

**Interfaces:**
- Consumes: `AppUserRepository`, `AuthTokenRepository` (Task 1).
- Produces: `SignupRequest(String email, String phone, String password)` — `email`/`password` `@NotBlank`, `phone` optional.
- Produces: `LoginRequest(String email, String password)` — both `@NotBlank`.
- Produces: `UpdateSessionRequest(UUID sessionId)` — `@NotNull`.
- Produces: `AuthResponse(String token, String email, String phone, UUID sessionId)`.
- Produces: `InvalidCredentialsException extends RuntimeException` (constructor takes a `String message`) — thrown for "email already in a login attempt with wrong password", "no such token", and "malformed Authorization header" alike, deliberately not distinguishing wrong-password from no-such-email in its message.
- Produces: `AuthService.signup(SignupRequest): AuthResponse`, `AuthService.login(LoginRequest): AuthResponse`, `AuthService.resolveUser(String token): AppUser`, `AuthService.me(String token): AuthResponse`, `AuthService.updateSession(String token, UpdateSessionRequest): AuthResponse`.

- [ ] **Step 1: Create the four DTOs and `InvalidCredentialsException`** as described in Interfaces, following this codebase's existing record-DTO and exception style (see `FamilyMemberCreateRequest.java` for the record pattern, `DuplicateResourceException.java` for the exception pattern — a single-arg `String message` constructor calling `super(message)`).

- [ ] **Step 2: Write the failing tests**

```java
@SpringBootTest
@Transactional
class AuthServiceTest {
    @Autowired private AuthService authService;
    @Autowired private AppUserRepository appUserRepository;

    @Test
    void signupCreatesAHashedPasswordAndAToken() {
        AuthResponse response = authService.signup(new SignupRequest("patient@example.com", "5551234567", "correct-horse"));

        assertThat(response.token()).isNotBlank();
        assertThat(response.sessionId()).isNull();
        AppUser saved = appUserRepository.findByEmail("patient@example.com").orElseThrow();
        assertThat(saved.getPasswordHash()).isNotEqualTo("correct-horse"); // actually hashed
    }

    @Test
    void signupWithADuplicateEmailIsRejected() {
        authService.signup(new SignupRequest("dup@example.com", null, "pw"));
        assertThatThrownBy(() -> authService.signup(new SignupRequest("dup@example.com", null, "pw2")))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void loginWithTheWrongPasswordIsRejected() {
        authService.signup(new SignupRequest("login@example.com", null, "right-password"));
        assertThatThrownBy(() -> authService.login(new LoginRequest("login@example.com", "wrong-password")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void loginWithAnUnknownEmailIsRejectedTheSameWayAsAWrongPassword() {
        assertThatThrownBy(() -> authService.login(new LoginRequest("nobody@example.com", "whatever")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void updateSessionPersistsTheSessionIdAndMeReturnsIt() {
        AuthResponse signedUp = authService.signup(new SignupRequest("session@example.com", null, "pw"));
        UUID sessionId = UUID.randomUUID();

        authService.updateSession(signedUp.token(), new UpdateSessionRequest(sessionId));

        assertThat(authService.me(signedUp.token()).sessionId()).isEqualTo(sessionId);
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `./mvnw.cmd test -Dtest=AuthServiceTest`
Expected: FAIL (`AuthService` doesn't exist yet)

- [ ] **Step 4: Implement `AuthService`**

- `signup`: check `appUserRepository.existsByEmail`, throw `DuplicateResourceException` if true (reuse the existing exception — this is the same "duplicate" shape FR-13 already uses elsewhere, no new exception type needed for this case). Hash the password with `new BCryptPasswordEncoder().hashpw(...)`/`encode(...)`, save a new `AppUser` (`sessionId` left `null`), generate a token as `UUID.randomUUID().toString()`, save a new `AuthToken` row pointing at the user, return `AuthResponse`.
- `login`: look up by email; if absent **or** `BCryptPasswordEncoder.matches(rawPassword, user.getPasswordHash())` is false, throw `InvalidCredentialsException` with the same message either way (don't branch the message on which case it was). On success, issue and save a fresh `AuthToken`, return `AuthResponse`.
- `resolveUser`: `authTokenRepository.findByToken(token)`, throw `InvalidCredentialsException` if absent, else return the associated `AppUser`.
- `me`: `resolveUser` then map to `AuthResponse`.
- `updateSession`: `resolveUser`, set `sessionId` on the user, save, return the updated `AuthResponse`.

- [ ] **Step 5: Run tests to verify they pass**

Run: `./mvnw.cmd test -Dtest=AuthServiceTest`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add QP/src/main/java/com/questionnaire/questionnaire_platform/dto/SignupRequest.java QP/src/main/java/com/questionnaire/questionnaire_platform/dto/LoginRequest.java QP/src/main/java/com/questionnaire/questionnaire_platform/dto/UpdateSessionRequest.java QP/src/main/java/com/questionnaire/questionnaire_platform/dto/AuthResponse.java QP/src/main/java/com/questionnaire/questionnaire_platform/exception/InvalidCredentialsException.java QP/src/main/java/com/questionnaire/questionnaire_platform/service/AuthService.java QP/src/test/java/com/questionnaire/questionnaire_platform/service/AuthServiceTest.java
git commit -m "feat: add AuthService for patient signup/login/session-linking"
```

---

## Task 3: AuthController + error wiring + CORS

**Files:**
- Create: `QP/src/main/java/com/questionnaire/questionnaire_platform/controller/AuthController.java`
- Modify: `QP/src/main/java/com/questionnaire/questionnaire_platform/exception/GlobalExceptionHandler.java`
- Create: `QP/src/main/java/com/questionnaire/questionnaire_platform/config/WebConfig.java`
- Test: `QP/src/test/java/com/questionnaire/questionnaire_platform/web/AuthControllerIntegrationTest.java`

**Interfaces:**
- Consumes: `AuthService` (Task 2).
- Produces: `POST /api/v1/auth/signup` (body `SignupRequest`) → `201 AuthResponse`.
- Produces: `POST /api/v1/auth/login` (body `LoginRequest`) → `200 AuthResponse`.
- Produces: `GET /api/v1/auth/me` (header `Authorization: Bearer <token>`) → `200 AuthResponse`.
- Produces: `POST /api/v1/auth/me/session` (header `Authorization`, body `UpdateSessionRequest`) → `200 AuthResponse`.
- Produces: `InvalidCredentialsException` → `401`, added to `GlobalExceptionHandler` the same way every other exception there is handled (`@ExceptionHandler`, `build(HttpStatus.UNAUTHORIZED, ex.getMessage(), request)`).
- Produces: CORS allowed for origin `http://localhost:5173` on path pattern `/api/v1/**`, all methods and headers, `allowCredentials(false)` (the frontend authenticates via a bearer token in the `Authorization` header, not cookies, so no credentialed requests are needed). This is confirmed to be currently **completely absent** from the backend — without it, the browser blocks every cross-origin call the Vite dev server makes, so this step is required, not optional polish.

- [ ] **Step 1: Write the failing tests**

```java
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerIntegrationTest {
    @Autowired private MockMvc mockMvc;

    @Test
    void signupThenLoginThenMeThenUpdateSessionAllSucceed() throws Exception {
        String signupBody = """
                {"email":"flow@example.com","phone":null,"password":"pw12345"}""";
        MvcResult signup = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON).content(signupBody))
                .andExpect(status().isCreated())
                .andReturn();
        String token = JsonPath.read(signup.getResponse().getContentAsString(), "$.token");

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").doesNotExist());

        String sessionId = UUID.randomUUID().toString();
        mockMvc.perform(post("/api/v1/auth/me/session")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"" + sessionId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(sessionId));
    }

    @Test
    void loginWithWrongPasswordReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"wrongpw@example.com\",\"password\":\"right\"}"));

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"wrongpw@example.com\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void signupWithDuplicateEmailReturns409() throws Exception {
        String body = "{\"email\":\"dupctrl@example.com\",\"password\":\"pw\"}";
        mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body));
        mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void corsAllowsTheViteDevOriginOnApiRoutes() throws Exception {
        mockMvc.perform(options("/api/v1/auth/login")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./mvnw.cmd test -Dtest=AuthControllerIntegrationTest`
Expected: FAIL (no `/api/v1/auth/**` routes exist yet)

- [ ] **Step 3: Implement `AuthController`**

`@RestController @RequestMapping("/api/v1/auth")`. For `/me` and `/me/session`, extract the token from the `Authorization` header with a small private helper (`strip "Bearer "`, throw `InvalidCredentialsException` if the header is missing or doesn't start with `"Bearer "`). Mirror the existing controllers' style exactly (`@Valid @RequestBody`, `ResponseEntity.status(HttpStatus.CREATED)` for signup, `.ok()` for the rest).

- [ ] **Step 4: Add the `InvalidCredentialsException` handler to `GlobalExceptionHandler`**, following the exact pattern already used for `DuplicateResourceException`/`ResourceNotFoundException` immediately above or below them in the file.

- [ ] **Step 5: Implement `WebConfig implements WebMvcConfigurer`**, overriding `addCorsMappings(CorsRegistry registry)` with `registry.addMapping("/api/v1/**").allowedOrigins("http://localhost:5173").allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS").allowedHeaders("*").allowCredentials(false)`. Annotate the class `@Configuration`.

- [ ] **Step 6: Run tests to verify they pass**

Run: `./mvnw.cmd test -Dtest=AuthControllerIntegrationTest`
Expected: PASS, including the new CORS test

- [ ] **Step 7: Run the full backend suite to confirm no regressions**

Run: `./mvnw.cmd test`
Expected: all tests pass (the pre-existing 106 plus the new ones from Tasks 1-3)

- [ ] **Step 8: Commit**

```bash
git add QP/src/main/java/com/questionnaire/questionnaire_platform/controller/AuthController.java QP/src/main/java/com/questionnaire/questionnaire_platform/exception/GlobalExceptionHandler.java QP/src/main/java/com/questionnaire/questionnaire_platform/config/WebConfig.java QP/src/test/java/com/questionnaire/questionnaire_platform/web/AuthControllerIntegrationTest.java
git commit -m "feat: add AuthController exposing signup/login/me/me-session, enable CORS for the dev frontend"
```

---

## Task 4: Frontend scaffold + API client

**Files:**
- Create: `patient-portal/package.json`, `patient-portal/vite.config.js`, `patient-portal/tailwind.config.js`, `patient-portal/postcss.config.js`, `patient-portal/index.html`
- Create: `patient-portal/src/main.jsx`
- Create: `patient-portal/src/App.jsx`
- Create: `patient-portal/src/api/client.js`
- Test: `patient-portal/src/api/client.test.js`

**Interfaces:**
- Produces, all in `client.js`, each a thin `fetch` wrapper against `import.meta.env.VITE_API_BASE_URL` (default `http://localhost:8081/api/v1`), throwing an `ApiError extends Error` (carrying `.status` and the backend's `message` field) on any non-2xx response:
  - `signup({email, phone, password})`, `login({email, password})` — no auth header.
  - `getMe(token)`, `updateSession(token, sessionId)` — `Authorization: Bearer <token>`.
  - `startAssessment()` — `POST /assessment/start` with `{sessionId: null}`.
  - `getCurrentTemplate(sessionId)`, `nextTemplate(sessionId)`, `submitAnswer(sessionId, {questionId, value, familyMemberId})`.
  - `getVisitedTemplates(sessionId)`, `viewVisitedTemplate(sessionId, templateId)`, `resumeAtTemplate(sessionId, templateId)`.
  - `addFamilyMember(sessionId, {relationshipType, maternalPaternalSide, scopeId})`, `getFamilyMembers(sessionId)`.
  - `getPedigreeExport(sessionId)`.

- [ ] **Step 1: Scaffold the Vite React project**

Run: `npm create vite@latest patient-portal -- --template react` from the repo root, then `cd patient-portal && npm install react-router-dom tailwindcss postcss autoprefixer && npx tailwindcss init -p`. Configure `tailwind.config.js`'s `content` to `["./index.html", "./src/**/*.{js,jsx}"]` and add the three `@tailwind` directives to a new `src/index.css`, imported from `main.jsx`.

- [ ] **Step 2: Write the failing tests**

Install test tooling: `npm install -D vitest @testing-library/react @testing-library/jest-dom jsdom`.

```javascript
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { login, getMe, ApiError } from './client';

describe('client', () => {
  beforeEach(() => { global.fetch = vi.fn(); });

  it('login posts credentials and returns the parsed body on success', async () => {
    global.fetch.mockResolvedValue({ ok: true, status: 200, json: async () => ({ token: 't', sessionId: null }) });
    const result = await login({ email: 'a@b.com', password: 'pw' });
    expect(result.token).toBe('t');
    expect(global.fetch).toHaveBeenCalledWith(
      expect.stringContaining('/auth/login'),
      expect.objectContaining({ method: 'POST' })
    );
  });

  it('throws ApiError carrying the backend message and status on a non-2xx response', async () => {
    global.fetch.mockResolvedValue({ ok: false, status: 401, json: async () => ({ message: 'bad credentials' }) });
    await expect(login({ email: 'a@b.com', password: 'wrong' })).rejects.toMatchObject({ status: 401, message: 'bad credentials' });
  });

  it('getMe sends the bearer token in the Authorization header', async () => {
    global.fetch.mockResolvedValue({ ok: true, status: 200, json: async () => ({ token: 't' }) });
    await getMe('my-token');
    expect(global.fetch).toHaveBeenCalledWith(
      expect.stringContaining('/auth/me'),
      expect.objectContaining({ headers: expect.objectContaining({ Authorization: 'Bearer my-token' }) })
    );
  });
});
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `npx vitest run src/api/client.test.js`
Expected: FAIL (`client.js` doesn't exist yet)

- [ ] **Step 4: Implement `client.js`**

One internal `request(path, { method, token, body })` helper doing the fetch, header assembly, and `ApiError` throw on `!res.ok`; every exported function listed in Interfaces calls it with the right path/method/body. No per-function test is needed beyond the three above — they all share the one code path being tested.

- [ ] **Step 5: Run tests to verify they pass**

Run: `npx vitest run src/api/client.test.js`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add patient-portal/
git commit -m "feat: scaffold patient-portal React app with API client"
```

---

## Task 5: Auth pages and `AuthContext`

**Files:**
- Create: `patient-portal/src/context/AuthContext.jsx`
- Create: `patient-portal/src/pages/SignupPage.jsx`
- Create: `patient-portal/src/pages/LoginPage.jsx`
- Create: `patient-portal/src/components/ProtectedRoute.jsx`
- Modify: `patient-portal/src/App.jsx`
- Test: `patient-portal/src/context/AuthContext.test.jsx`

**Interfaces:**
- Consumes: `signup`, `login` from `client.js` (Task 4).
- Produces: `AuthProvider` (wraps the app), `useAuth()` hook returning `{ token, login(email, password), signup(email, phone, password), logout() }`. Persists `token` to `localStorage` under the key `qp_auth_token` and restores it on mount.
- Produces: `<ProtectedRoute>` — redirects to `/login` when `useAuth().token` is falsy, otherwise renders its children.

- [ ] **Step 1: Write the failing test**

```jsx
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { vi } from 'vitest';
import { AuthProvider, useAuth } from './AuthContext';
import * as client from '../api/client';

function Probe() {
  const { token, login } = useAuth();
  return (
    <div>
      <span data-testid="token">{token ?? 'none'}</span>
      <button onClick={() => login('a@b.com', 'pw')}>go</button>
    </div>
  );
}

it('login stores the returned token and exposes it via the hook', async () => {
  vi.spyOn(client, 'login').mockResolvedValue({ token: 'abc123', sessionId: null });
  render(<AuthProvider><Probe /></AuthProvider>);

  fireEvent.click(screen.getByText('go'));

  await waitFor(() => expect(screen.getByTestId('token')).toHaveTextContent('abc123'));
  expect(localStorage.getItem('qp_auth_token')).toBe('abc123');
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npx vitest run src/context/AuthContext.test.jsx`
Expected: FAIL (`AuthContext.jsx` doesn't exist yet)

- [ ] **Step 3: Implement `AuthContext.jsx`**, `SignupPage.jsx`, `LoginPage.jsx`, `ProtectedRoute.jsx`

`AuthContext` holds `token` in state, initialized from `localStorage.getItem('qp_auth_token')`; `login`/`signup` call the matching `client.js` function, store the result's `token` into both state and `localStorage`. `SignupPage`/`LoginPage` are plain forms (email/phone/password, email/password respectively) calling `useAuth().signup`/`.login` on submit and navigating to `/assessment` on success, showing the thrown `ApiError.message` on failure.

- [ ] **Step 4: Wire `App.jsx`** with `react-router-dom` routes: `/signup`, `/login`, and `/assessment` wrapped in `<ProtectedRoute>`.

- [ ] **Step 5: Run test to verify it passes**

Run: `npx vitest run src/context/AuthContext.test.jsx`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add patient-portal/src/context patient-portal/src/pages/SignupPage.jsx patient-portal/src/pages/LoginPage.jsx patient-portal/src/components/ProtectedRoute.jsx patient-portal/src/App.jsx
git commit -m "feat: add signup/login pages and AuthContext"
```

---

## Task 6: Session bootstrap on `AssessmentPage`

**Files:**
- Create: `patient-portal/src/pages/AssessmentPage.jsx`
- Test: `patient-portal/src/pages/AssessmentPage.test.jsx`

**Interfaces:**
- Consumes: `useAuth()` (Task 5), `getMe`, `startAssessment`, `updateSession`, `getCurrentTemplate` (Task 4).
- Produces: `AssessmentPage` — on mount, resolves a `sessionId` (existing or newly created) into local state and renders `<TemplateView sessionId={sessionId} />` (Task 8) once resolved; renders a loading state until then.

- [ ] **Step 1: Write the failing test**

```jsx
import { render, screen, waitFor } from '@testing-library/react';
import { vi } from 'vitest';
import AssessmentPage from './AssessmentPage';
import * as client from '../api/client';

vi.mock('../context/AuthContext', () => ({ useAuth: () => ({ token: 'tok' }) }));

it('starts a new session and persists it when the user has none yet', async () => {
  vi.spyOn(client, 'getMe').mockResolvedValue({ sessionId: null });
  vi.spyOn(client, 'startAssessment').mockResolvedValue({ sessionId: 'new-session-id' });
  const updateSpy = vi.spyOn(client, 'updateSession').mockResolvedValue({});
  vi.spyOn(client, 'getCurrentTemplate').mockResolvedValue({ templateCode: 'DEMOGRAPHICS', questions: [], completed: false });

  render(<AssessmentPage />);

  await waitFor(() => expect(updateSpy).toHaveBeenCalledWith('tok', 'new-session-id'));
});

it('resumes the existing session without starting a new one', async () => {
  vi.spyOn(client, 'getMe').mockResolvedValue({ sessionId: 'existing-session-id' });
  const startSpy = vi.spyOn(client, 'startAssessment');
  vi.spyOn(client, 'getCurrentTemplate').mockResolvedValue({ templateCode: 'PARENTS', questions: [], completed: false });

  render(<AssessmentPage />);

  await waitFor(() => expect(client.getCurrentTemplate).toHaveBeenCalledWith('existing-session-id'));
  expect(startSpy).not.toHaveBeenCalled();
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npx vitest run src/pages/AssessmentPage.test.jsx`
Expected: FAIL (`AssessmentPage.jsx` doesn't exist yet)

- [ ] **Step 3: Implement `AssessmentPage.jsx`**

On mount: `getMe(token)` → if `sessionId` is present, use it; else call `startAssessment()`, then `updateSession(token, result.sessionId)`, then use that `sessionId`. Once resolved, render `<TemplateView sessionId={sessionId} familyMemberId={null} subjectLabel="you" />` (a placeholder component is fine here — Task 8 implements it fully).

- [ ] **Step 4: Run test to verify it passes**

Run: `npx vitest run src/pages/AssessmentPage.test.jsx`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add patient-portal/src/pages/AssessmentPage.jsx
git commit -m "feat: resolve or start the patient's session on load"
```

---

## Task 7: `QuestionField` — the dynamic, type-driven renderer

**Files:**
- Create: `patient-portal/src/components/QuestionField.jsx`
- Test: `patient-portal/src/components/QuestionField.test.jsx`

**Interfaces:**
- Consumes: a question object shaped exactly like the backend's `QuestionAnswerView` — `{ id, answerKey, text, type, displayOrder, options, validation, visible, value }`, where `type` is one of `"TEXT" | "NUMBER" | "DATE" | "RADIO" | "CHECKBOX" | "DROPDOWN"` and `options` is `null` or `[{label, value}]`.
- Produces: `<QuestionField question={...} onChange={(value) => void} />` — calls `onChange` with a value shaped correctly for that `type`: a string for `TEXT`/`DATE`/`RADIO`/`DROPDOWN`-with-string-values, a number for `NUMBER`, the matching option's raw `value` for `RADIO`/`DROPDOWN` (which may itself be a boolean or string per the backend's options, e.g. `{label:"Yes", value:true}`), and **always an array** for `CHECKBOX`, even with one item selected.
- Returns nothing exported besides the default component — this task defines no new types other kinds of code depend on; `TemplateView` (Task 8) is the only consumer.

- [ ] **Step 1: Write the failing tests**

```jsx
import { render, screen, fireEvent } from '@testing-library/react';
import { vi } from 'vitest';
import QuestionField from './QuestionField';

it('renders a TEXT question as a text input and reports string changes', () => {
  const onChange = vi.fn();
  render(<QuestionField question={{ id: '1', answerKey: 'first_name', text: 'First name', type: 'TEXT', options: null, validation: { required: true }, visible: true, value: null }} onChange={onChange} />);

  fireEvent.change(screen.getByLabelText('First name'), { target: { value: 'Sarah' } });
  expect(onChange).toHaveBeenCalledWith('Sarah');
});

it('renders RADIO options from the options array and reports the option\'s raw value, not its label', () => {
  const onChange = vi.fn();
  render(<QuestionField question={{ id: '2', answerKey: 'sex_assigned_at_birth', text: 'Sex', type: 'RADIO', options: [{ label: 'Male', value: 'MALE' }, { label: 'Female', value: 'FEMALE' }], validation: null, visible: true, value: null }} onChange={onChange} />);

  fireEvent.click(screen.getByLabelText('Female'));
  expect(onChange).toHaveBeenCalledWith('FEMALE');
});

it('renders CHECKBOX as multi-select and always reports an array, even for one selection', () => {
  const onChange = vi.fn();
  render(<QuestionField question={{ id: '3', answerKey: 'ethnicity', text: 'Ethnicity', type: 'CHECKBOX', options: [{ label: 'A', value: 'A' }, { label: 'B', value: 'B' }], validation: null, visible: true, value: null }} onChange={onChange} />);

  fireEvent.click(screen.getByLabelText('A'));
  expect(onChange).toHaveBeenCalledWith(['A']);
});

it('does not render anything when visible is false', () => {
  const { container } = render(<QuestionField question={{ id: '4', answerKey: 'age', text: 'Age', type: 'NUMBER', options: null, validation: null, visible: false, value: null }} onChange={vi.fn()} />);
  expect(container).toBeEmptyDOMElement();
});
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `npx vitest run src/components/QuestionField.test.jsx`
Expected: FAIL (`QuestionField.jsx` doesn't exist yet)

- [ ] **Step 3: Implement `QuestionField.jsx`**

Return `null` immediately if `!question.visible`. Switch on `question.type`: `TEXT`→`<input type="text">` (add `pattern`/`maxLength`/`minLength` attributes from `validation` when present), `NUMBER`→`<input type="number">` (`min`/`max` from `validation`), `DATE`→`<input type="date">`, `RADIO`/`DROPDOWN`→ iterate `options`, `CHECKBOX`→ iterate `options` as checkboxes, maintaining local selected-array state and calling `onChange` with the whole array on every toggle. Every input's `<label>` text is `question.text`, associated via `htmlFor`/`id` so `getByLabelText` queries resolve (matches the tests above). Mark required fields (`validation?.required`) with a visible `*`.

- [ ] **Step 4: Run tests to verify they pass**

Run: `npx vitest run src/components/QuestionField.test.jsx`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add patient-portal/src/components/QuestionField.jsx patient-portal/src/components/QuestionField.test.jsx
git commit -m "feat: add dynamic type-driven QuestionField renderer"
```

---

## Task 8: `TemplateView` — renders a template, submits answers, navigates

**Files:**
- Create: `patient-portal/src/components/TemplateView.jsx`
- Modify: `patient-portal/src/pages/AssessmentPage.jsx`
- Test: `patient-portal/src/components/TemplateView.test.jsx`

**Interfaces:**
- Consumes: `QuestionField` (Task 7); `submitAnswer`, `nextTemplate`, `ApiError` (Task 4).
- Produces: `<TemplateView sessionId={...} familyMemberId={...|null} subjectLabel={"you"|string} onCompleted={() => void} onFamilyMemberPassEnded={() => void} />`. Fetches the current template itself (`getCurrentTemplate(sessionId)`) on mount and whenever `familyMemberId` changes; renders one `QuestionField` per question; on each field's `onChange`, calls `submitAnswer(sessionId, { questionId, value, familyMemberId })` and re-fetches the current template (so visibility rules that just changed are reflected). A "Next" button calls `nextTemplate(sessionId)`: on success with `completed: true`, calls `onCompleted()`; on success with a different `familyMemberId` than before, calls `onFamilyMemberPassEnded()`; on a caught `ApiError` with `status === 409`, displays `error.message` (the backend already names the missing required fields in it) instead of navigating.
- Produces: a persistent banner at the top of the rendered template reading `"You are now answering for: " + subjectLabel` whenever `familyMemberId` is non-null, and nothing (no banner) when it is `null` — so a patient who has switched into a relative's pass is never left guessing whose data they're entering. `subjectLabel` is supplied by the parent (`AssessmentPage`, Task 10's wiring), not derived inside `TemplateView` itself.

- [ ] **Step 1: Write the failing tests**

```jsx
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { vi } from 'vitest';
import TemplateView from './TemplateView';
import * as client from '../api/client';

it('renders the questions from the fetched template', async () => {
  vi.spyOn(client, 'getCurrentTemplate').mockResolvedValue({
    templateCode: 'DEMOGRAPHICS', completed: false, familyMemberId: null,
    questions: [{ id: 'q1', answerKey: 'first_name', text: 'First name', type: 'TEXT', options: null, validation: null, visible: true, value: null }],
  });

  render(<TemplateView sessionId="s1" familyMemberId={null} subjectLabel="you" onCompleted={vi.fn()} onFamilyMemberPassEnded={vi.fn()} />);

  await waitFor(() => expect(screen.getByLabelText('First name')).toBeInTheDocument());
});

it('shows a "you are now answering for" banner only when familyMemberId is set', async () => {
  vi.spyOn(client, 'getCurrentTemplate').mockResolvedValue({ templateCode: 'DEMOGRAPHICS', completed: false, familyMemberId: 'fm1', questions: [] });

  const { rerender } = render(<TemplateView sessionId="s1" familyMemberId="fm1" subjectLabel="Mother" onCompleted={vi.fn()} onFamilyMemberPassEnded={vi.fn()} />);
  await waitFor(() => expect(screen.getByText('You are now answering for: Mother')).toBeInTheDocument());

  rerender(<TemplateView sessionId="s1" familyMemberId={null} subjectLabel="you" onCompleted={vi.fn()} onFamilyMemberPassEnded={vi.fn()} />);
  await waitFor(() => expect(screen.queryByText(/You are now answering for/)).not.toBeInTheDocument());
});

it('shows the backend\'s 409 message instead of navigating when required fields are missing', async () => {
  vi.spyOn(client, 'getCurrentTemplate').mockResolvedValue({ templateCode: 'DEMOGRAPHICS', completed: false, familyMemberId: null, questions: [] });
  const err = Object.assign(new Error("required question(s) unanswered: first_name"), { status: 409 });
  vi.spyOn(client, 'nextTemplate').mockRejectedValue(err);
  const onCompleted = vi.fn();

  render(<TemplateView sessionId="s1" familyMemberId={null} onCompleted={onCompleted} onFamilyMemberPassEnded={vi.fn()} />);
  fireEvent.click(await screen.findByText('Next'));

  await waitFor(() => expect(screen.getByText(/first_name/)).toBeInTheDocument());
  expect(onCompleted).not.toHaveBeenCalled();
});

it('calls onCompleted when next-template reports completed:true', async () => {
  vi.spyOn(client, 'getCurrentTemplate').mockResolvedValue({ templateCode: 'EXTENDED_FAMILY', completed: false, familyMemberId: null, questions: [] });
  vi.spyOn(client, 'nextTemplate').mockResolvedValue({ completed: true });
  const onCompleted = vi.fn();

  render(<TemplateView sessionId="s1" familyMemberId={null} onCompleted={onCompleted} onFamilyMemberPassEnded={vi.fn()} />);
  fireEvent.click(await screen.findByText('Next'));

  await waitFor(() => expect(onCompleted).toHaveBeenCalled());
});
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `npx vitest run src/components/TemplateView.test.jsx`
Expected: FAIL (`TemplateView.jsx` doesn't exist yet)

- [ ] **Step 3: Implement `TemplateView.jsx`** per the Interfaces description above.

- [ ] **Step 4: Wire it into `AssessmentPage.jsx`** in place of the Task 6 placeholder, passing the resolved `sessionId`, `familyMemberId={null}` and `subjectLabel="you"` initially, and handlers that (for `onCompleted`) show the Task 11 completion page and (for `onFamilyMemberPassEnded`) simply re-render `TemplateView` with the updated `familyMemberId` from the latest `getCurrentTemplate` response (`subjectLabel` stays `"you"` here — Task 10 is what actually drives it to a relationship name when the patient switches into a family member's pass).

- [ ] **Step 5: Run tests to verify they pass**

Run: `npx vitest run src/components/TemplateView.test.jsx`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add patient-portal/src/components/TemplateView.jsx patient-portal/src/pages/AssessmentPage.jsx
git commit -m "feat: add TemplateView rendering, answer submission, and navigation"
```

---

## Task 9: Visited-templates navigation (go back and edit)

**Files:**
- Create: `patient-portal/src/components/VisitedTemplatesNav.jsx`
- Modify: `patient-portal/src/components/TemplateView.jsx`
- Test: `patient-portal/src/components/VisitedTemplatesNav.test.jsx`

**Interfaces:**
- Consumes: `getVisitedTemplates`, `resumeAtTemplate` (Task 4).
- Produces: `<VisitedTemplatesNav sessionId={...} onResumed={(templateResponse) => void} />` — fetches and lists visited templates by `templateName`; clicking one calls `resumeAtTemplate(sessionId, templateId)` and passes the result to `onResumed`.

- [ ] **Step 1: Write the failing test**

```jsx
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { vi } from 'vitest';
import VisitedTemplatesNav from './VisitedTemplatesNav';
import * as client from '../api/client';

it('lists visited templates and resumes the one clicked', async () => {
  vi.spyOn(client, 'getVisitedTemplates').mockResolvedValue([
    { templateId: 't1', templateCode: 'DEMOGRAPHICS', templateName: 'Demographics' },
    { templateId: 't2', templateCode: 'PARENTS', templateName: 'Parents' },
  ]);
  const resumed = { templateCode: 'DEMOGRAPHICS', questions: [] };
  vi.spyOn(client, 'resumeAtTemplate').mockResolvedValue(resumed);
  const onResumed = vi.fn();

  render(<VisitedTemplatesNav sessionId="s1" onResumed={onResumed} />);
  fireEvent.click(await screen.findByText('Demographics'));

  await waitFor(() => expect(client.resumeAtTemplate).toHaveBeenCalledWith('s1', 't1'));
  expect(onResumed).toHaveBeenCalledWith(resumed);
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npx vitest run src/components/VisitedTemplatesNav.test.jsx`
Expected: FAIL (`VisitedTemplatesNav.jsx` doesn't exist yet)

- [ ] **Step 3: Implement `VisitedTemplatesNav.jsx`**, then render it inside `TemplateView.jsx` (sidebar or top strip — implementer's call), feeding its `onResumed` result into the same state `TemplateView` already uses to display the current template.

- [ ] **Step 4: Run test to verify it passes**

Run: `npx vitest run src/components/VisitedTemplatesNav.test.jsx`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add patient-portal/src/components/VisitedTemplatesNav.jsx patient-portal/src/components/TemplateView.jsx
git commit -m "feat: add visited-templates navigation for going back and editing"
```

---

## Task 10: Family member panel (add + switch pass)

**Files:**
- Create: `patient-portal/src/components/FamilyMemberPanel.jsx`
- Modify: `patient-portal/src/pages/AssessmentPage.jsx`
- Test: `patient-portal/src/components/FamilyMemberPanel.test.jsx`

**Interfaces:**
- Consumes: `addFamilyMember`, `getFamilyMembers` (Task 4).
- Produces: a hardcoded `RELATIONSHIP_TYPES` array (`MOTHER, FATHER, BROTHER, SISTER, SON, DAUGHTER, SPOUSE, HALF_BROTHER, HALF_SISTER, ADOPTED_SON, ADOPTED_DAUGHTER, GRANDFATHER, GRANDMOTHER, UNCLE, AUNT, COUSIN, NIECE, NEPHEW`) and `MATERNAL_PATERNAL_SIDES` (`MATERNAL, PATERNAL, UNKNOWN`) — the one deliberate exception to "never hardcode," per the spec's "Decided: what's hardcoded vs. generic" section.
- Produces: `<FamilyMemberPanel sessionId={...} onSwitchedToFamilyMember={(familyMemberId, relationshipType) => void} />` — lists existing family members (by their `roles[0].relationshipType`, falling back to "Family member" if `roles` is empty), a form to add a new one (relationship type + side, `scopeId` omitted so it defaults to the proband), and a button per newly-created member to switch into their pass. `relationshipType` is passed back alongside `familyMemberId` so the caller can show it in `TemplateView`'s "You are now answering for" banner (Task 8) without `TemplateView` needing to know anything about family members itself.

- [ ] **Step 1: Write the failing test**

```jsx
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { vi } from 'vitest';
import FamilyMemberPanel from './FamilyMemberPanel';
import * as client from '../api/client';

it('adds a family member and offers to switch into their pass', async () => {
  vi.spyOn(client, 'getFamilyMembers').mockResolvedValue([]);
  const created = { familyMemberId: 'fm1', templateCode: 'DEMOGRAPHICS' };
  vi.spyOn(client, 'addFamilyMember').mockResolvedValue(created);
  const onSwitched = vi.fn();

  render(<FamilyMemberPanel sessionId="s1" onSwitchedToFamilyMember={onSwitched} />);

  fireEvent.change(await screen.findByLabelText('Relationship'), { target: { value: 'MOTHER' } });
  fireEvent.change(screen.getByLabelText('Side'), { target: { value: 'MATERNAL' } });
  fireEvent.click(screen.getByText('Add family member'));

  await waitFor(() => expect(client.addFamilyMember).toHaveBeenCalledWith('s1', { relationshipType: 'MOTHER', maternalPaternalSide: 'MATERNAL' }));
  expect(onSwitched).toHaveBeenCalledWith('fm1', 'MOTHER');
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npx vitest run src/components/FamilyMemberPanel.test.jsx`
Expected: FAIL (`FamilyMemberPanel.jsx` doesn't exist yet)

- [ ] **Step 3: Implement `FamilyMemberPanel.jsx`** per Interfaces — submitting the add-form immediately calls `onSwitchedToFamilyMember` with the new `familyMemberId` and the chosen `relationshipType` (matching the test, which expects the switch to happen right away rather than needing a second click, since adding a family member already starts their pass on the backend).

- [ ] **Step 4: Wire it into `AssessmentPage.jsx`**: render `FamilyMemberPanel` alongside `TemplateView`; its `onSwitchedToFamilyMember(familyMemberId, relationshipType)` updates both the `familyMemberId` state and a `subjectLabel` state (set to `relationshipType`, e.g. `"MOTHER"`) that are passed into `TemplateView`, which re-fetches the current template (now the family member's) and shows the Task 8 banner using the new `subjectLabel`. When `TemplateView`'s `onFamilyMemberPassEnded` fires, reset `subjectLabel` back to `"you"` alongside `familyMemberId` back to `null`.

- [ ] **Step 5: Run test to verify it passes**

Run: `npx vitest run src/components/FamilyMemberPanel.test.jsx`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add patient-portal/src/components/FamilyMemberPanel.jsx patient-portal/src/pages/AssessmentPage.jsx
git commit -m "feat: add family member panel with add-and-switch-pass flow"
```

---

## Task 11: Completion page + onboarding walkthrough

**Files:**
- Create: `patient-portal/src/pages/CompletionPage.jsx`
- Create: `patient-portal/src/components/OnboardingWalkthrough.jsx`
- Modify: `patient-portal/src/App.jsx`, `patient-portal/src/pages/AssessmentPage.jsx`
- Test: `patient-portal/src/pages/CompletionPage.test.jsx`

**Interfaces:**
- Consumes: `getPedigreeExport` (Task 4).
- Produces: `<CompletionPage sessionId={...} />` — on mount, calls `getPedigreeExport(sessionId)`; shows a plain "Thank you, your assessment is complete" message on success, or a generic "something went wrong, please contact support" message plus a "Try again" button if the call fails (the patient never sees the JSON itself, per the spec). Clicking "Try again" re-calls `getPedigreeExport(sessionId)` and re-renders the same success/error outcome — a patient alone at home with a transient network blip must have a way forward other than a dead end.
- Produces: `<OnboardingWalkthrough onDismiss={() => void} />` — a simple dismissible overlay with 2-3 steps explaining "Answer questions here," "Add a family member here," "Go back and edit here"; stores a `qp_onboarding_seen` flag in `localStorage` so it only ever shows once.

- [ ] **Step 1: Write the failing test**

```jsx
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { vi } from 'vitest';
import CompletionPage from './CompletionPage';
import * as client from '../api/client';

it('shows a thank-you message once the pedigree export succeeds', async () => {
  vi.spyOn(client, 'getPedigreeExport').mockResolvedValue({ individuals: {} });
  render(<CompletionPage sessionId="s1" />);
  await waitFor(() => expect(screen.getByText(/thank you/i)).toBeInTheDocument());
});

it('shows a generic error message and a retry button if the export call fails, and retrying can succeed', async () => {
  const spy = vi.spyOn(client, 'getPedigreeExport')
    .mockRejectedValueOnce(new Error('boom'))
    .mockResolvedValueOnce({ individuals: {} });
  render(<CompletionPage sessionId="s1" />);
  await waitFor(() => expect(screen.getByText(/something went wrong/i)).toBeInTheDocument());

  fireEvent.click(screen.getByText('Try again'));

  await waitFor(() => expect(screen.getByText(/thank you/i)).toBeInTheDocument());
  expect(spy).toHaveBeenCalledTimes(2);
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npx vitest run src/pages/CompletionPage.test.jsx`
Expected: FAIL (`CompletionPage.jsx` doesn't exist yet)

- [ ] **Step 3: Implement `CompletionPage.jsx`** and `OnboardingWalkthrough.jsx` per Interfaces.

- [ ] **Step 4: Wire both into the app**: `AssessmentPage`'s `onCompleted` (from Task 8) navigates to `/completion` (add the route in `App.jsx`); `AssessmentPage` renders `OnboardingWalkthrough` once on mount if `localStorage.getItem('qp_onboarding_seen')` is unset.

- [ ] **Step 5: Run test to verify it passes**

Run: `npx vitest run src/pages/CompletionPage.test.jsx`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add patient-portal/src/pages/CompletionPage.jsx patient-portal/src/components/OnboardingWalkthrough.jsx patient-portal/src/App.jsx patient-portal/src/pages/AssessmentPage.jsx
git commit -m "feat: add completion page and first-run onboarding walkthrough"
```

---

## Task 12: Responsive styling pass and manual cross-viewport check

**Files:**
- Modify: `patient-portal/src/pages/*.jsx`, `patient-portal/src/components/*.jsx` (Tailwind class changes only — no new components, no logic changes)

**Interfaces:**
- Consumes: every component built in Tasks 5-11.
- Produces: nothing new — this task only adds responsive Tailwind utility classes (`flex-col md:flex-row`, `w-full md:w-auto`, etc.) to existing markup.

- [ ] **Step 1: Run the dev server**

Run: `npm run dev` (from `patient-portal/`)

- [ ] **Step 2: Manually check every page at a 375px-wide viewport (phone) and a 1280px-wide viewport (desktop)** — signup, login, the assessment flow (including the family-member panel and visited-templates nav side by side with the question list), and the completion page. Adjust Tailwind classes wherever content overflows horizontally or controls become too small to tap.

- [ ] **Step 3: Confirm no horizontal scrollbar appears on the body at 375px width** on every page from Step 2.

- [ ] **Step 4: Run the full frontend test suite one more time to confirm the styling pass didn't break any component logic**

Run: `npx vitest run`
Expected: all tests from Tasks 4-11 still pass

- [ ] **Step 5: Commit**

```bash
git add patient-portal/src
git commit -m "style: responsive pass for desktop and mobile viewports"
```

---

## Self-Review Notes

- **Spec coverage**: every numbered goal in the spec maps to a task — accounts/login (1-3, 5), schema-driven rendering (7), all `QuestionType`s + validation + visibility (7), navigation forward/back (8-9), family-member switching (10), pedigree-export verification (11), responsive (12). Non-goals are explicitly not covered by any task, which is correct.
- **Review Focus**: all five items are each pinned to the specific task's own test, not left as a general aspiration.
- **Type consistency checked**: `QuestionAnswerView` shape (Task 7's Interfaces) matches what `TemplateView` (Task 8) fetches and passes down; `AuthResponse.sessionId` (Task 2) matches what `AssessmentPage` (Task 6) reads via `getMe`; `FamilyMemberCreateRequest`'s `relationshipType`/`maternalPaternalSide` field names (Task 10) match the real backend DTO field names exactly; `subjectLabel`/`familyMemberId` are threaded consistently from `FamilyMemberPanel` (Task 10) through `AssessmentPage` into `TemplateView`'s banner (Task 8).
- **Proportion**: 12 tasks for a 2-day, 2-surface (backend auth + full frontend) build is tight but each task is independently small and testable; no task's code is fully written out in the plan — only signatures, exact test assertions, and the specific values the spec pins (BCrypt, opaque token, hardcoded enum lists).
- **Decisions confirmed in a follow-up review pass** (post-initial-draft): CORS was found to be completely unconfigured on the backend — a real blocker, not an edge case — and a step was added to Task 3 to fix it; a "whose pass am I in" banner was added to Task 8 so a patient switched into a relative's pass is never left guessing; a "Try again" button was added to Task 11's completion page so a failed pedigree-export call isn't a dead end. Confirmed as acceptable for Phase 1 as originally written: no logout action, no `AuthToken` expiry, reusing `DuplicateResourceException` for "email already registered," and the BCrypt library default cost factor.
