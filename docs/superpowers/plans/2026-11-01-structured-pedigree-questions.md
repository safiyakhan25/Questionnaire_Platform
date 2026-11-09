# Structured Pedigree Questions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the seven existing-but-unanswerable pedigree questions (`diseases`, `genetic_findings`, `gender_transition`, `twinship`, `adoption`, `pregnancy_outcome`, `assisted_reproduction`) answerable through the real UI, plus fix the `country_of_origin` regression, without touching any of the seven backend answer-writers.

**Architecture:** One new `QuestionType.STRUCTURED` backed by a new `question.field_config` JSONB column describing a flat or repeatable set of sub-fields (reusing existing TEXT/NUMBER/DATE/DROPDOWN semantics, plus two new frontend-only field kinds for picking an existing family member and one for a repeatable nested row-group). The seven questions are reconfigured in place via the existing admin API — no new templates, no writer changes.

**Tech Stack:** Spring Boot 4.1 / Java 21 / Jackson 3 (`tools.jackson.*`) / Lombok / Liquibase / PostgreSQL (backend, `QP/`); Vite / React 19 / Tailwind / Vitest / Playwright (frontend, `patient-portal/`).

**Spec:** `docs/superpowers/specs/2026-11-01-structured-pedigree-questions-design.md`

## Global Constraints

- Never commit or push — the user makes all commits herself. Every task's last step is `git add` of the changed files only, never `git commit`.
- All seven answer-writers (`DiseaseAnswerWriter`, `MutationAnswerWriter`, `GenderTransitionAnswerWriter`, `TwinshipAnswerWriter`, `AdoptionAnswerWriter`, `PregnancyOutcomeAnswerWriter`, `AssistedReproductionAnswerWriter`) are a fixed contract — no task may modify them.
- None of the seven questions may be seeded with `validation: {"required": true}` (spec §4.2) — `null`/`[]` means both "not applicable" and "not yet answered" and the completion check can't tell them apart.
- `field_config` JSON for each question is copied verbatim from spec §5 — do not improvise option lists, labels, or field names.
- Dev DB: `questionnaire_platform_final`, psql at `"/c/Program Files/PostgreSQL/14/bin/psql.exe"`, user `questionnaire_app` / `questionnaire_app_pw`. Backend runs via `./mvnw.cmd spring-boot:run -Dspring-boot.run.arguments=--server.port=8081 -Dmaven.test.skip=true` from `QP/` — not currently running; start it only in Task 5.

## Review Focus

- A `STRUCTURED` question saved with `validation.required: true` — the spec explicitly forbids this (§4.2); `ConfigurationValidator` should reject it at save time, not rely on the seeding convention alone (Task 2).
- A structured answer submitted with a malformed inner shape (e.g. `twinship` with `twinType` but no `coTwinFamilyMemberIds`) — the writer already throws `InvalidAnswerException`, but nothing currently proves that exception surfaces correctly through the full `STRUCTURED` submit path rather than being swallowed or mis-typed (Task 4).
- `GROUP_LIST` submitted as an empty array (e.g. `assisted_reproduction` with `anonymous: true` and no participants) must not be treated as a missing required field when the `GROUP_LIST` field itself is `required: false` (Task 8 and Task 10).
- Re-opening a `STRUCTURED` question that already has a non-null stored answer must prefill the "Does this apply?" toggle as "Yes" and prefill every sub-field from the existing value, not reset to blank (Task 10).
- `FamilyMemberSelect`/`FamilyMemberMultiSelect` must exclude the subject currently answering (so a family member can't pick themselves as their own co-twin/adoptive parent/participant) (Task 7).

---

## File structure

Backend (`QP/src/main/java/com/questionnaire/questionnaire_platform/`):
- `src/main/resources/db/changelog/changes/029-add-field-config-to-question.yaml` — new migration
- `entity/QuestionType.java` — add `STRUCTURED`
- `entity/Question.java` — add `fieldConfig` column
- `dto/QuestionDto.java`, `dto/QuestionCreateRequest.java`, `dto/QuestionUpdateRequest.java`, `dto/QuestionAnswerView.java` — add `fieldConfig`
- `service/QuestionService.java` — wire `fieldConfig` through create/update/toDto
- `service/AssessmentService.java` — `toView()` reads `fieldConfig`
- `engine/ConfigurationValidator.java` — `validateFieldConfig`, extend `validateForType`
- `engine/AnswerValidator.java` — `STRUCTURED` case
- `src/test/java/.../support/TestFixtures.java` — new `createQuestion` overload
- `src/test/java/.../engine/ConfigurationValidatorTest.java`, `src/test/java/.../engine/AnswerValidatorTest.java` — new cases
- `src/test/java/.../service/AdminConfigurationIntegrationTest.java` — new cases
- `src/test/java/.../service/StructuredQuestionIntegrationTest.java` — new file

Frontend (`patient-portal/src/`):
- `components/SimpleFields.jsx` — new (extracted TEXT/NUMBER/DATE/DROPDOWN + `useSyncedValue`)
- `components/FamilyMemberLabel.js` — new (extracted from `FamilyMemberHub.jsx`)
- `components/FamilyMemberSelect.jsx` — new
- `components/GroupListField.jsx` — new
- `components/FieldRenderer.jsx` — new
- `components/StructuredQuestionField.jsx` — new
- `components/QuestionField.jsx` — refactored to use `SimpleFields`, new `STRUCTURED` branch, two new props
- `components/TemplateView.jsx` — passes the two new props through
- `components/FamilyMemberHub.jsx` — `labelFor` replaced by the shared `FamilyMemberLabel` helper
- Matching `*.test.jsx` files co-located per existing convention

Dev DB (no repo files): the 7 questions reconfigured in place + `country_of_origin` reseeded, via the existing admin API (Task 11).

---

### Task 1: `field_config` column + `STRUCTURED` type, plumbed end to end

**Files:**
- Create: `QP/src/main/resources/db/changelog/changes/029-add-field-config-to-question.yaml`
- Modify: `QP/src/main/resources/db/changelog/db.changelog-master.yaml` (include the new file, same pattern as 028)
- Modify: `QP/src/main/java/com/questionnaire/questionnaire_platform/entity/QuestionType.java`
- Modify: `QP/src/main/java/com/questionnaire/questionnaire_platform/entity/Question.java`
- Modify: `QP/src/main/java/com/questionnaire/questionnaire_platform/dto/QuestionDto.java`
- Modify: `QP/src/main/java/com/questionnaire/questionnaire_platform/dto/QuestionCreateRequest.java`
- Modify: `QP/src/main/java/com/questionnaire/questionnaire_platform/dto/QuestionUpdateRequest.java`
- Modify: `QP/src/main/java/com/questionnaire/questionnaire_platform/dto/QuestionAnswerView.java`
- Modify: `QP/src/main/java/com/questionnaire/questionnaire_platform/service/QuestionService.java`
- Modify: `QP/src/main/java/com/questionnaire/questionnaire_platform/service/AssessmentService.java:700-704` (`toView()`)
- Test: `QP/src/test/java/com/questionnaire/questionnaire_platform/service/AdminConfigurationIntegrationTest.java`

**Interfaces:**
- Produces: `QuestionType.STRUCTURED` enum constant; `Question.getFieldConfig()/setFieldConfig(String)` (raw JSON string, same pattern as `getOptions()`); `QuestionDto.fieldConfig()` / `QuestionCreateRequest.fieldConfig()` / `QuestionUpdateRequest.fieldConfig()` / `QuestionAnswerView.fieldConfig()` (all `JsonNode`, same pattern as `options`).

- [ ] **Step 1: Write the failing test** in `AdminConfigurationIntegrationTest.java`:

```java
@Test
void creatingAStructuredQuestionPersistsAndReturnsItsFieldConfig() {
    JsonNode fieldConfig = objectMapper.readTree("""
        {"shape":"list","fields":[
          {"name":"diseaseType","label":"Disease name","type":"TEXT","required":true},
          {"name":"ageAtDiagnosis","label":"Age at diagnosis","type":"NUMBER","required":false}
        ]}""");
    UUID templateId = TestFixtures.createTemplate(templateService, workflowId, "T1", 1, null, SubjectScope.BOTH);
    QuestionDto created = questionService.create(templateId,
            new QuestionCreateRequest("diseases_test", "Diseases", QuestionType.STRUCTURED, 1, null, null, null, fieldConfig));

    QuestionDto fetched = questionService.getById(created.id());
    assertThat(fetched.type()).isEqualTo(QuestionType.STRUCTURED);
    assertThat(fetched.fieldConfig().path("shape").asString()).isEqualTo("list");
    assertThat(fetched.fieldConfig().path("fields").get(0).path("name").asString()).isEqualTo("diseaseType");
}
```
(Match this test's exact constructor-argument order to whatever order Step 3 below settles on for `QuestionCreateRequest`; `objectMapper`/`questionService`/`templateService`/`workflowId` already exist as fixtures in this file — reuse them, don't reintroduce.)

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw.cmd test -Dtest=AdminConfigurationIntegrationTest#creatingAStructuredQuestionPersistsAndReturnsItsFieldConfig` (from `QP/`)
Expected: FAIL to compile — `STRUCTURED` and `fieldConfig` don't exist yet.

- [ ] **Step 3: Implement the plumbing**

1. `QuestionType.java`: add `STRUCTURED` to the enum.
2. New migration `029-add-field-config-to-question.yaml`, one `addColumn` changeSet: `question.field_config`, type `JSONB`, nullable. Include it in `db.changelog-master.yaml` immediately after `028`.
3. `Question.java`: add `fieldConfig` field, exactly mirroring the existing `options` field — `@JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb") private String fieldConfig;`.
4. `QuestionDto.java`: add `JsonNode fieldConfig` as the last record component; in `from()`, add `jsonColumnMapper.read(question.getFieldConfig())` as the last constructor argument.
5. `QuestionCreateRequest.java` / `QuestionUpdateRequest.java`: add `JsonNode fieldConfig` as the last record component (no `@NotNull` — nullable for every non-`STRUCTURED` type).
6. `QuestionAnswerView.java`: add `JsonNode fieldConfig` as the last record component.
7. `QuestionService.java`: in `create()` and `update()`, add `question.setFieldConfig(jsonColumnMapper.write(request.fieldConfig()))` alongside the existing `setOptions`/`setValidation` calls.
8. `AssessmentService.java` `toView()` (around line 700): add `jsonColumnMapper.read(question.getFieldConfig())` as the last argument to `new QuestionAnswerView(...)`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw.cmd test -Dtest=AdminConfigurationIntegrationTest` (from `QP/`)
Expected: PASS, including every pre-existing test in the file (constructor-argument order changes ripple to every other `QuestionCreateRequest`/`QuestionUpdateRequest` call site in this file and in `TestFixtures.createQuestion` — fix those call sites to pass `null` for the new trailing `fieldConfig` argument rather than leaving them broken).

- [ ] **Step 5: Stage**

```bash
git add QP/src/main/resources/db/changelog/changes/029-add-field-config-to-question.yaml QP/src/main/resources/db/changelog/db.changelog-master.yaml QP/src/main/java/com/questionnaire/questionnaire_platform/entity/QuestionType.java QP/src/main/java/com/questionnaire/questionnaire_platform/entity/Question.java QP/src/main/java/com/questionnaire/questionnaire_platform/dto/QuestionDto.java QP/src/main/java/com/questionnaire/questionnaire_platform/dto/QuestionCreateRequest.java QP/src/main/java/com/questionnaire/questionnaire_platform/dto/QuestionUpdateRequest.java QP/src/main/java/com/questionnaire/questionnaire_platform/dto/QuestionAnswerView.java QP/src/main/java/com/questionnaire/questionnaire_platform/service/QuestionService.java QP/src/main/java/com/questionnaire/questionnaire_platform/service/AssessmentService.java QP/src/test/java/com/questionnaire/questionnaire_platform/service/AdminConfigurationIntegrationTest.java QP/src/test/java/com/questionnaire/questionnaire_platform/support/TestFixtures.java
```

---

### Task 2: `ConfigurationValidator` validates `field_config` and rejects `required: true` on `STRUCTURED`

**Files:**
- Modify: `QP/src/main/java/com/questionnaire/questionnaire_platform/engine/ConfigurationValidator.java`
- Modify: `QP/src/main/java/com/questionnaire/questionnaire_platform/service/QuestionService.java` (pass `fieldConfig`/`validation` together into `validateForType`)
- Test: `QP/src/test/java/com/questionnaire/questionnaire_platform/engine/ConfigurationValidatorTest.java`
- Test: `QP/src/test/java/com/questionnaire/questionnaire_platform/service/AdminConfigurationIntegrationTest.java`

**Interfaces:**
- Consumes: `QuestionType.STRUCTURED` (Task 1).
- Produces: `ConfigurationValidator.validateForType(QuestionType type, JsonNode validation, JsonNode options, JsonNode fieldConfig)` (signature change — update both call sites in `QuestionService.create()`/`update()`); `InvalidConfigurationException` on a malformed `fieldConfig` or on `STRUCTURED` + `validation.required == true`.

- [ ] **Step 1: Write the failing tests** in `ConfigurationValidatorTest.java`:

```java
@Test
void acceptsAWellFormedListShapeFieldConfig() {
    JsonNode fieldConfig = objectMapper.readTree("""
        {"shape":"list","fields":[{"name":"gene","label":"Gene","type":"TEXT","required":true}]}""");
    assertThatCode(() -> configurationValidator.validateForType(QuestionType.STRUCTURED, null, null, fieldConfig))
            .doesNotThrowAnyException();
}

@Test
void rejectsFieldConfigWithAnUnknownFieldType() {
    JsonNode fieldConfig = objectMapper.readTree("""
        {"shape":"object","fields":[{"name":"x","label":"X","type":"BOGUS","required":true}]}""");
    assertThatThrownBy(() -> configurationValidator.validateForType(QuestionType.STRUCTURED, null, null, fieldConfig))
            .isInstanceOf(InvalidConfigurationException.class);
}

@Test
void rejectsAStructuredQuestionConfiguredAsRequired() {
    JsonNode validation = objectMapper.readTree("{\"required\": true}");
    JsonNode fieldConfig = objectMapper.readTree("""
        {"shape":"object","fields":[{"name":"x","label":"X","type":"TEXT","required":true}]}""");
    assertThatThrownBy(() -> configurationValidator.validateForType(QuestionType.STRUCTURED, validation, null, fieldConfig))
            .isInstanceOf(InvalidConfigurationException.class)
            .hasMessageContaining("STRUCTURED");
}

@Test
void aGroupListFieldRequiresItsOwnNestedFieldsArray() {
    JsonNode fieldConfig = objectMapper.readTree("""
        {"shape":"object","fields":[{"name":"participants","label":"Participants","type":"GROUP_LIST","required":false}]}""");
    assertThatThrownBy(() -> configurationValidator.validateForType(QuestionType.STRUCTURED, null, null, fieldConfig))
            .isInstanceOf(InvalidConfigurationException.class);
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./mvnw.cmd test -Dtest=ConfigurationValidatorTest` (from `QP/`)
Expected: FAIL — `validateForType` doesn't yet take a `fieldConfig` parameter / doesn't yet reject anything.

- [ ] **Step 3: Implement `validateFieldConfig(JsonNode fieldConfig)` and extend `validateForType`**

In `ConfigurationValidator.java`:
- `validateFieldConfig(JsonNode fieldConfig)`: `null` is valid (non-`STRUCTURED` questions never set it). Otherwise require an `OBJECT` node; `shape` must be the string `"object"` or `"list"`; `fields` must be a non-empty `ARRAY`; each entry must have a non-blank `name`, non-blank `label`, and `type` in `{TEXT, NUMBER, DATE, DROPDOWN, FAMILY_MEMBER_SELECT, FAMILY_MEMBER_MULTI_SELECT, GROUP_LIST}`; a `DROPDOWN` entry must have a non-empty `options` array (reuse the existing `validateOptions` shape check on it); a `GROUP_LIST` entry must itself have a non-empty `fields` array (recurse one level — no deeper nesting is validated or needed).
- `validateForType(QuestionType type, JsonNode validation, JsonNode options, JsonNode fieldConfig)`: keep the existing two calls, add `validateFieldConfig(fieldConfig)`, then: `if (type == QuestionType.STRUCTURED && validation != null && validation.path("required").asBoolean(false)) throw new InvalidConfigurationException("'validation.required' cannot be true for a STRUCTURED question — see docs/superpowers/specs/2026-11-01-structured-pedigree-questions-design.md §4.2");`.
- Update both call sites in `QuestionService.java` (`create`/`update`) to pass `request.fieldConfig()` as the fourth argument.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./mvnw.cmd test -Dtest=ConfigurationValidatorTest,AdminConfigurationIntegrationTest` (from `QP/`)
Expected: PASS.

- [ ] **Step 5: Stage**

```bash
git add QP/src/main/java/com/questionnaire/questionnaire_platform/engine/ConfigurationValidator.java QP/src/main/java/com/questionnaire/questionnaire_platform/service/QuestionService.java QP/src/test/java/com/questionnaire/questionnaire_platform/engine/ConfigurationValidatorTest.java QP/src/test/java/com/questionnaire/questionnaire_platform/service/AdminConfigurationIntegrationTest.java
```

---

### Task 3: `AnswerValidator` accepts a `STRUCTURED` answer

**Files:**
- Modify: `QP/src/main/java/com/questionnaire/questionnaire_platform/engine/AnswerValidator.java:36-42`
- Test: `QP/src/test/java/com/questionnaire/questionnaire_platform/engine/AnswerValidatorTest.java`

**Interfaces:**
- Consumes: `QuestionType.STRUCTURED` (Task 1).

- [ ] **Step 1: Write the failing test** in `AnswerValidatorTest.java`:

```java
@Test
void structuredAnswerPassesWithoutTypeSpecificValidation() {
    Question question = /* existing test helper building a Question with answerKey "twinship" */;
    question.setType(QuestionType.STRUCTURED);
    JsonNode value = objectMapper.readTree("{\"twinType\":\"MONOZYGOTIC\",\"coTwinFamilyMemberIds\":[\"11111111-1111-1111-1111-111111111111\"]}");
    assertThatCode(() -> answerValidator.validate(question, value)).doesNotThrowAnyException();
}

@Test
void requiredStructuredAnswerStillRejectsBlank() {
    Question question = /* same helper */;
    question.setType(QuestionType.STRUCTURED);
    question.setValidation(/* raw JSON string */ "{\"required\":true}");
    assertThatThrownBy(() -> answerValidator.validate(question, NullNode.getInstance()))
            .isInstanceOf(InvalidAnswerException.class);
}
```
(Match the exact `Question`-building helper already used by the rest of this test file — don't invent a new one.)

- [ ] **Step 2: Run tests to verify they fail**

Run: `./mvnw.cmd test -Dtest=AnswerValidatorTest` (from `QP/`)
Expected: first test fails to compile (`QuestionType.STRUCTURED` not yet reachable from this module until Task 1 lands — if Task 1 is already merged, this test instead fails because the switch has no `STRUCTURED` branch and nothing throws, which is actually the desired behavior, so write this test to assert *no exception*, confirming it passes once Step 3 is a no-op case. The second test fails because the pre-switch `required` check already exists and should already pass — keep it only as a regression guard, not a new behavior.)

- [ ] **Step 3: Implement**

In `AnswerValidator.java`, add `case STRUCTURED -> {}` to the switch (line ~41), with a one-line comment: deep shape validation for a structured answer is the responsibility of its answer-writer (`DiseaseAnswerWriter` etc.), which already throws `InvalidAnswerException` on a malformed shape.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./mvnw.cmd test -Dtest=AnswerValidatorTest` (from `QP/`)
Expected: PASS.

- [ ] **Step 5: Stage**

```bash
git add QP/src/main/java/com/questionnaire/questionnaire_platform/engine/AnswerValidator.java QP/src/test/java/com/questionnaire/questionnaire_platform/engine/AnswerValidatorTest.java
```

---

### Task 4: End-to-end `STRUCTURED` submission for all seven questions

**Files:**
- Modify: `QP/src/test/java/com/questionnaire/questionnaire_platform/support/TestFixtures.java`
- Create: `QP/src/test/java/com/questionnaire/questionnaire_platform/service/StructuredQuestionIntegrationTest.java`

**Interfaces:**
- Consumes: `AssessmentService.submitAnswer(...)` (existing — exact signature already used by every other integration test in this package); `TestFixtures.createWorkflow/createTemplate` (existing).
- Produces: `TestFixtures.createQuestion(QuestionService, UUID templateId, String answerKey, QuestionType type, int displayOrder, JsonNode options, JsonNode validation, JsonNode visibilityRule, JsonNode fieldConfig)` — new overload, additive (the existing 7-arg overload stays, delegating with `fieldConfig = null`, so no other test file needs to change).

- [ ] **Step 1: Add the `TestFixtures` overload (no test needed — a fixture helper, proven by the tests that use it in Step 2)**

```java
public static UUID createQuestion(QuestionService questionService, UUID templateId, String answerKey, QuestionType type,
                                   int displayOrder, JsonNode options, JsonNode validation, JsonNode visibilityRule,
                                   JsonNode fieldConfig) {
    return questionService.create(templateId,
            new QuestionCreateRequest(answerKey, answerKey, type, displayOrder, options, validation, visibilityRule, fieldConfig)).id();
}
```
Keep the existing 7-arg overload, changing its body to call this one with `fieldConfig = null`.

- [ ] **Step 2: Write the failing tests** in new file `StructuredQuestionIntegrationTest.java` (model its `@SpringBootTest @Transactional` setup, `AssessmentStartRequest`/session-creation boilerplate, and `@BeforeEach` exactly on `PedigreeExportServiceTest.java`'s existing pattern — reuse its imports and fixture style, don't invent a new one). One workflow/template per question, `field_config` copied verbatim from spec §5. Seven happy-path tests plus one malformed-shape test:

```java
@Test
void diseasesStructuredAnswerPersistsThroughTheRealWriter() {
    // submit [{"diseaseType":"Breast Cancer","ageAtDiagnosis":45}] via assessmentService.submitAnswer(...)
    // assert diseaseRepository.findAllByPersonId(probandId) has one row with diseaseCatalog.name == "Breast Cancer"
}

@Test
void geneticFindingsStructuredAnswerPersistsThroughTheRealWriter() { /* ["gene":"BRCA1","mutation":"c.68_69delAG"] -> mutationRepository */ }

@Test
void genderTransitionStructuredAnswerPersistsThroughTheRealWriter() { /* {"fromGender":"MAN","toGender":"WOMAN"} -> genderTransitionRepository */ }

@Test
void twinshipStructuredAnswerPersistsThroughTheRealWriter() {
    // create a second family member first, submit {"twinType":"MONOZYGOTIC","coTwinFamilyMemberIds":[thatId]}
    // assert twinshipMemberRepository has 2 rows in the same twinship
}

@Test
void adoptionStructuredAnswerPersistsThroughTheRealWriter() {
    // create a family member to be the adoptive parent, submit {"adoptionType":"ADOPTED_IN","adoptiveParentFamilyMemberIds":[thatId]}
    // assert adoptionParentRepository has one row
}

@Test
void pregnancyOutcomeStructuredAnswerPersistsThroughTheRealWriter() {
    // on a FAMILY_MEMBER_ONLY subject, submit {"outcomeType":"SAB","gestationalWeeks":10}
    // assert pregnancyOutcomeRepository row
}

@Test
void assistedReproductionStructuredAnswerWithEmptyParticipantsPersistsThroughTheRealWriter() {
    // submit {"reproductionType":"ANONYMOUS_DONOR","anonymous":true,"participants":[]}
    // assert assistedReproductionRepository row exists, assistedReproductionParticipantRepository has zero rows for it
    // (Review Focus: an empty GROUP_LIST on a non-required field must not be rejected)
}

@Test
void malformedStructuredAnswerIsRejectedWithTheWritersOwnMessage() {
    // submit {"twinType":"MONOZYGOTIC"} (missing coTwinFamilyMemberIds) to the twinship question
    assertThatThrownBy(() -> assessmentService.submitAnswer(sessionId, new AnswerSubmitRequest(twinshipQuestionId, value, null)))
            .isInstanceOf(InvalidAnswerException.class)
            .hasMessageContaining("coTwinFamilyMemberIds");
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `./mvnw.cmd test -Dtest=StructuredQuestionIntegrationTest` (from `QP/`)
Expected: FAIL — the dispatch from `answerKey` to each writer already exists in `AssessmentService` (not a new mechanism), so these should mostly fail only insofar as `STRUCTURED`/`fieldConfig` aren't wired yet; confirm the failure is "class/field not found," not a logic error, before moving on.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./mvnw.cmd test -Dtest=StructuredQuestionIntegrationTest` (from `QP/`)
Expected: PASS. If a test fails on dispatch (writer not invoked), that is a pre-existing wiring gap outside this plan's scope — stop and report rather than modifying a writer.

- [ ] **Step 5: Run the full backend suite**

Run: `./mvnw.cmd test` (from `QP/`)
Expected: PASS, same count as before plus these new tests (no regressions).

- [ ] **Step 6: Stage**

```bash
git add QP/src/test/java/com/questionnaire/questionnaire_platform/support/TestFixtures.java QP/src/test/java/com/questionnaire/questionnaire_platform/service/StructuredQuestionIntegrationTest.java
```

---

### Task 5: `SimpleFields.jsx` extraction — behavior-preserving refactor

**Files:**
- Create: `patient-portal/src/components/SimpleFields.jsx`
- Modify: `patient-portal/src/components/QuestionField.jsx`
- Test: `patient-portal/src/components/QuestionField.test.jsx` (existing — must pass unchanged)
- Test: `patient-portal/src/components/SimpleFields.test.jsx` (new)

**Interfaces:**
- Produces: `useSyncedValue(externalValue)` (moved verbatim from `QuestionField.jsx`); `TextField({ fieldId, label, value, required, validation, onChange })`, `NumberField({ fieldId, label, value, required, validation, onChange })`, `DateField({ fieldId, label, value, required, onChange })`, `DropdownField({ fieldId, label, value, required, options, onChange })` — each renders exactly the JSX currently inline in `QuestionField.jsx` for that type (same ids, same `aria-required`, same `onChange` payload shape), just parameterized.

- [ ] **Step 1: Write the failing test** in new `SimpleFields.test.jsx` — one case per extracted component proving the exact `onChange` payload, mirroring the existing TEXT/NUMBER/DATE/DROPDOWN cases already in `QuestionField.test.jsx` (lines 5-19, 37-43) but calling `TextField`/`NumberField`/`DateField`/`DropdownField` directly:

```jsx
it('TextField reports string changes', () => {
  const onChange = vi.fn();
  render(<TextField fieldId="q" label="First name" value={null} required={false} validation={null} onChange={onChange} />);
  fireEvent.change(screen.getByLabelText('First name'), { target: { value: 'Sarah' } });
  expect(onChange).toHaveBeenCalledWith('Sarah');
});
```
(Four such tests total — one per component — matching each existing `QuestionField.test.jsx` assertion.)

- [ ] **Step 2: Run tests to verify they fail**

Run: `npx vitest run src/components/SimpleFields.test.jsx` (from `patient-portal/`)
Expected: FAIL — module doesn't exist.

- [ ] **Step 3: Extract the four components + `useSyncedValue` into `SimpleFields.jsx`**

Move `useSyncedValue` (lines 13-24) and the TEXT/NUMBER/DATE/DROPDOWN JSX blocks (lines 35-60, 63-88, 90-113, 142-172) out of `QuestionField.jsx` verbatim, parameterized by the props listed in Interfaces above instead of destructuring from a `question` object. Keep `RequiredMark` local to `SimpleFields.jsx` (exported, since `QuestionField.jsx` still uses it for RADIO/CHECKBOX) or duplicate the 4-line component in `QuestionField.jsx` — implementer's call, either is fine since it's tiny and has no state.

- [ ] **Step 4: Rewrite `QuestionField.jsx`'s TEXT/NUMBER/DATE/DROPDOWN branches to call the extracted components**, passing through `question`'s fields. RADIO/CHECKBOX branches are untouched.

- [ ] **Step 5: Run both test files to verify everything passes**

Run: `npx vitest run src/components/SimpleFields.test.jsx src/components/QuestionField.test.jsx` (from `patient-portal/`)
Expected: PASS — `QuestionField.test.jsx` must pass with zero edits to that file (proves the refactor is behavior-preserving).

- [ ] **Step 6: Stage**

```bash
git add patient-portal/src/components/SimpleFields.jsx patient-portal/src/components/SimpleFields.test.jsx patient-portal/src/components/QuestionField.jsx
```

---

### Task 6: `FamilyMemberSelect` / `FamilyMemberMultiSelect`

**Files:**
- Create: `patient-portal/src/components/FamilyMemberLabel.js`
- Create: `patient-portal/src/components/FamilyMemberSelect.jsx`
- Modify: `patient-portal/src/components/FamilyMemberHub.jsx` (use the extracted label helper instead of its own local `labelFor`)
- Test: `patient-portal/src/components/FamilyMemberSelect.test.jsx`
- Test: `patient-portal/src/components/FamilyMemberHub.test.jsx` (existing — must still pass)

**Interfaces:**
- Consumes: `client.getFamilyMembers(sessionId)` (existing, `patient-portal/src/api/client.js:60-61`).
- Produces: `familyMemberLabel(member)` (moved from `FamilyMemberHub.jsx`'s `labelFor`, same logic: `member.roles?.[0]?.relationshipType ?? 'Family member'`); `FamilyMemberSelect({ sessionId, excludeId, multiple, value, onChange })` — `multiple=false` renders a `<select>` submitting one id (or `null`); `multiple=true` renders a checkbox list submitting an array of ids (same pattern as the existing CHECKBOX branch in `QuestionField.jsx`: always an array, never a single value).

- [ ] **Step 1: Write the failing tests** in `FamilyMemberSelect.test.jsx` (mock `client.getFamilyMembers` exactly as `FamilyMemberHub.test.jsx` does):

```jsx
it('single-select mode lists family members except the excluded id and reports the chosen id', async () => {
  vi.spyOn(client, 'getFamilyMembers').mockResolvedValue([
    { id: 'fm1', roles: [{ relationshipType: 'MOTHER' }] },
    { id: 'fm2', roles: [{ relationshipType: 'SISTER' }] },
  ]);
  const onChange = vi.fn();
  render(<FamilyMemberSelect sessionId="s1" excludeId="fm2" multiple={false} value={null} onChange={onChange} />);
  const select = await screen.findByRole('combobox');
  expect(screen.queryByText('SISTER')).not.toBeInTheDocument();
  fireEvent.change(select, { target: { value: 'fm1' } });
  expect(onChange).toHaveBeenCalledWith('fm1');
});

it('multi-select mode reports an array of chosen ids', async () => {
  vi.spyOn(client, 'getFamilyMembers').mockResolvedValue([{ id: 'fm1', roles: [{ relationshipType: 'MOTHER' }] }]);
  const onChange = vi.fn();
  render(<FamilyMemberSelect sessionId="s1" excludeId={null} multiple value={[]} onChange={onChange} />);
  fireEvent.click(await screen.findByLabelText('MOTHER'));
  expect(onChange).toHaveBeenCalledWith(['fm1']);
});
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `npx vitest run src/components/FamilyMemberSelect.test.jsx` (from `patient-portal/`)
Expected: FAIL — module doesn't exist.

- [ ] **Step 3: Implement**

1. `FamilyMemberLabel.js`: `export function familyMemberLabel(member) { return member.roles?.[0]?.relationshipType ?? 'Family member'; }`.
2. `FamilyMemberHub.jsx`: replace its local `labelFor` with an import of `familyMemberLabel`, keep every call site (`labelFor(member)` → `familyMemberLabel(member)`).
3. `FamilyMemberSelect.jsx`: on mount, fetch `getFamilyMembers(sessionId)`, filter out `excludeId`, render single-`<select>` or checkbox-list per `multiple`, using `familyMemberLabel` for display and `member.id` for the submitted value(s) — mirror the existing CHECKBOX toggle logic in `QuestionField.jsx` (lines 174-206) for the `multiple` branch.

- [ ] **Step 4: Run tests to verify they pass**

Run: `npx vitest run src/components/FamilyMemberSelect.test.jsx src/components/FamilyMemberHub.test.jsx` (from `patient-portal/`)
Expected: PASS.

- [ ] **Step 5: Stage**

```bash
git add patient-portal/src/components/FamilyMemberLabel.js patient-portal/src/components/FamilyMemberSelect.jsx patient-portal/src/components/FamilyMemberSelect.test.jsx patient-portal/src/components/FamilyMemberHub.jsx
```

---

### Task 7: `GroupListField` — repeatable rows

**Files:**
- Create: `patient-portal/src/components/GroupListField.jsx`
- Test: `patient-portal/src/components/GroupListField.test.jsx`

**Interfaces:**
- Produces: `GroupListField({ fields, value, onChange, renderField })` — `value` is always an array of row-objects (`[]` when empty); `renderField(fieldDef, rowValue, onRowFieldChange)` is supplied by the caller (Task 9's `FieldRenderer`) so this component has no dependency on field-type dispatch itself; renders one row per `value` entry plus an "Add" control, and a "Remove" control per row; removing/adding calls `onChange` with the full updated array (same full-replace convention every other multi-value control in this codebase already uses).

- [ ] **Step 1: Write the failing tests** in `GroupListField.test.jsx`, using a trivial `renderField` stub (no real field types needed to test the repeat/add/remove mechanics):

```jsx
function stubRenderField(fieldDef, rowValue, onRowFieldChange) {
  return <input aria-label={fieldDef.label} value={rowValue?.[fieldDef.name] ?? ''}
    onChange={(e) => onRowFieldChange(fieldDef.name, e.target.value)} />;
}

it('starts with zero rows and adding a row appends an empty object', () => {
  const onChange = vi.fn();
  render(<GroupListField fields={[{ name: 'role', label: 'Role' }]} value={[]} onChange={onChange} renderField={stubRenderField} />);
  fireEvent.click(screen.getByText(/add/i));
  expect(onChange).toHaveBeenCalledWith([{}]);
});

it('editing a field in a row updates only that row', () => {
  const onChange = vi.fn();
  render(<GroupListField fields={[{ name: 'role', label: 'Role' }]} value={[{ role: 'A' }, { role: 'B' }]} onChange={onChange} renderField={stubRenderField} />);
  fireEvent.change(screen.getAllByLabelText('Role')[1], { target: { value: 'C' } });
  expect(onChange).toHaveBeenCalledWith([{ role: 'A' }, { role: 'C' }]);
});

it('removing a row drops it from the array', () => {
  const onChange = vi.fn();
  render(<GroupListField fields={[{ name: 'role', label: 'Role' }]} value={[{ role: 'A' }, { role: 'B' }]} onChange={onChange} renderField={stubRenderField} />);
  fireEvent.click(screen.getAllByText(/remove/i)[0]);
  expect(onChange).toHaveBeenCalledWith([{ role: 'B' }]);
});
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `npx vitest run src/components/GroupListField.test.jsx` (from `patient-portal/`)
Expected: FAIL — module doesn't exist.

- [ ] **Step 3: Implement `GroupListField.jsx`** per the Interfaces block above — plain array mapping, no internal state beyond what's needed for the add/remove buttons (the array itself is fully controlled via `value`/`onChange`, same pattern as CHECKBOX).

- [ ] **Step 4: Run tests to verify they pass**

Run: `npx vitest run src/components/GroupListField.test.jsx` (from `patient-portal/`)
Expected: PASS.

- [ ] **Step 5: Stage**

```bash
git add patient-portal/src/components/GroupListField.jsx patient-portal/src/components/GroupListField.test.jsx
```

---

### Task 8: `FieldRenderer` — dispatches one `FieldDef`

**Files:**
- Create: `patient-portal/src/components/FieldRenderer.jsx`
- Test: `patient-portal/src/components/FieldRenderer.test.jsx`

**Interfaces:**
- Consumes: `TextField`/`NumberField`/`DateField`/`DropdownField` (Task 5), `FamilyMemberSelect` (Task 6), `GroupListField` (Task 7).
- Produces: `FieldRenderer({ field, value, onChange, sessionId, excludeId })` — switches on `field.type`: `TEXT`/`NUMBER`/`DATE`/`DROPDOWN` → the matching `SimpleFields` component (`fieldId` derived from `field.name`, `label` from `field.label`, `required` from `field.required`, `options` from `field.options`); `FAMILY_MEMBER_SELECT` → `<FamilyMemberSelect multiple={false} .../>`; `FAMILY_MEMBER_MULTI_SELECT` → `<FamilyMemberSelect multiple .../>`; `GROUP_LIST` → `<GroupListField fields={field.fields} renderField={(f, rowValue, onRowChange) => <FieldRenderer field={f} value={rowValue?.[f.name]} onChange={(v) => onRowChange(f.name, v)} sessionId={sessionId} excludeId={excludeId} />} .../>` (this is how the circular-dependency concern from the design is avoided — `GroupListField` never imports `FieldRenderer`, `FieldRenderer` passes itself in as a closure).

- [ ] **Step 1: Write the failing tests** in `FieldRenderer.test.jsx` — one case per `field.type` proving it delegates to the right sub-component and the right `onChange` payload, plus one case proving the `GROUP_LIST` closure wiring (a two-level test: add a row, fill its `TEXT` sub-field, assert the array shape):

```jsx
it('GROUP_LIST: adding a row and filling a TEXT sub-field produces the nested array', () => {
  const field = { name: 'participants', label: 'Participants', type: 'GROUP_LIST', fields: [
    { name: 'role', label: 'Role', type: 'TEXT', required: true },
  ]};
  const onChange = vi.fn();
  render(<FieldRenderer field={field} value={[]} onChange={onChange} sessionId="s1" excludeId={null} />);
  fireEvent.click(screen.getByText(/add/i));
  expect(onChange).toHaveBeenCalledWith([{}]);
});
```
(Plus one straightforward case each for `TEXT`, `DROPDOWN`, `FAMILY_MEMBER_SELECT`, `FAMILY_MEMBER_MULTI_SELECT` confirming the right sub-component rendered — mock `client.getFamilyMembers` for the latter two per Task 6's pattern.)

- [ ] **Step 2: Run tests to verify they fail**

Run: `npx vitest run src/components/FieldRenderer.test.jsx` (from `patient-portal/`)
Expected: FAIL — module doesn't exist.

- [ ] **Step 3: Implement `FieldRenderer.jsx`** per the Interfaces block above.

- [ ] **Step 4: Run tests to verify they pass**

Run: `npx vitest run src/components/FieldRenderer.test.jsx` (from `patient-portal/`)
Expected: PASS.

- [ ] **Step 5: Stage**

```bash
git add patient-portal/src/components/FieldRenderer.jsx patient-portal/src/components/FieldRenderer.test.jsx
```

---

### Task 9: `StructuredQuestionField` — the top-level `STRUCTURED` renderer

**Files:**
- Create: `patient-portal/src/components/StructuredQuestionField.jsx`
- Test: `patient-portal/src/components/StructuredQuestionField.test.jsx`

**Interfaces:**
- Consumes: `FieldRenderer` (Task 8).
- Produces: `StructuredQuestionField({ question, sessionId, excludeId, onChange })` — reads `question.fieldConfig` (`{shape, fields}`) and `question.value`. For `shape: "object"`: renders a "Does this apply to this person?" Yes/No toggle, defaulted to "Yes" when `question.value` is non-null and "No" when it is `null`; "No" submits `null` and hides the fields; "Yes" renders one `FieldRenderer` per `field` in `fields`, pre-filled from `question.value?.[field.name]`, assembling a local object and calling `onChange` with the full object on every change (merge semantics, not replace-on-blur — same immediacy as every other field in this codebase). For `shape: "list"`: delegates directly to `GroupListField` (via `FieldRenderer`-style per-row rendering, reusing the exact same closure pattern as Task 8's `GROUP_LIST` case — the top-level list *is* a group-list, there is no separate "apply?" toggle for it, matching `diseases`/`genetic_findings` which are optional by being empty, not by being null) with `value={question.value ?? []}`.

- [ ] **Step 1: Write the failing tests** in `StructuredQuestionField.test.jsx`:

```jsx
it('shape "object": defaults the toggle to No and submits null when the user leaves it at No', () => {
  const question = { fieldConfig: { shape: 'object', fields: [{ name: 'fromGender', label: 'From', type: 'TEXT', required: true }] }, value: null };
  render(<StructuredQuestionField question={question} sessionId="s1" excludeId={null} onChange={vi.fn()} />);
  expect(screen.queryByLabelText('From')).not.toBeInTheDocument();
});

it('shape "object": a non-null existing value defaults the toggle to Yes and pre-fills fields', () => {
  const question = { fieldConfig: { shape: 'object', fields: [{ name: 'fromGender', label: 'From', type: 'TEXT', required: true }] }, value: { fromGender: 'MAN' } };
  render(<StructuredQuestionField question={question} sessionId="s1" excludeId={null} onChange={vi.fn()} />);
  expect(screen.getByLabelText('From')).toHaveValue('MAN');
});

it('shape "object": switching to Yes then filling a field submits the assembled object', () => {
  const question = { fieldConfig: { shape: 'object', fields: [{ name: 'fromGender', label: 'From', type: 'TEXT', required: true }] }, value: null };
  const onChange = vi.fn();
  render(<StructuredQuestionField question={question} sessionId="s1" excludeId={null} onChange={onChange} />);
  fireEvent.click(screen.getByLabelText(/yes/i));
  fireEvent.change(screen.getByLabelText('From'), { target: { value: 'MAN' } });
  expect(onChange).toHaveBeenCalledWith({ fromGender: 'MAN' });
});

it('shape "list": renders with no apply-toggle and submits the row array directly', () => {
  const question = { fieldConfig: { shape: 'list', fields: [{ name: 'diseaseType', label: 'Disease name', type: 'TEXT', required: true }] }, value: [] };
  const onChange = vi.fn();
  render(<StructuredQuestionField question={question} sessionId="s1" excludeId={null} onChange={onChange} />);
  expect(screen.queryByLabelText(/yes/i)).not.toBeInTheDocument();
  fireEvent.click(screen.getByText(/add/i));
  expect(onChange).toHaveBeenCalledWith([{}]);
});
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `npx vitest run src/components/StructuredQuestionField.test.jsx` (from `patient-portal/`)
Expected: FAIL — module doesn't exist.

- [ ] **Step 3: Implement `StructuredQuestionField.jsx`** per the Interfaces block above.

- [ ] **Step 4: Run tests to verify they pass**

Run: `npx vitest run src/components/StructuredQuestionField.test.jsx` (from `patient-portal/`)
Expected: PASS.

- [ ] **Step 5: Stage**

```bash
git add patient-portal/src/components/StructuredQuestionField.jsx patient-portal/src/components/StructuredQuestionField.test.jsx
```

---

### Task 10: Wire `STRUCTURED` into `QuestionField` and thread `sessionId`/subject id from `TemplateView`

**Files:**
- Modify: `patient-portal/src/components/QuestionField.jsx`
- Modify: `patient-portal/src/components/TemplateView.jsx:100-106`
- Test: `patient-portal/src/components/QuestionField.test.jsx`
- Test: `patient-portal/src/components/TemplateView.test.jsx` (existing — must still pass)

**Interfaces:**
- Consumes: `StructuredQuestionField` (Task 9).
- Produces: `QuestionField({ question, onChange, sessionId, familyMemberId })` — two new props, both optional (default `undefined`/`null`) so every other call site in the codebase (if any besides `TemplateView`) keeps working unchanged.

- [ ] **Step 1: Write the failing test** in `QuestionField.test.jsx`:

```jsx
it('renders a STRUCTURED question via StructuredQuestionField, passing sessionId and the subject id through', () => {
  const question = { id: 'q1', answerKey: 'twinship', text: 'Twinship', type: 'STRUCTURED',
    fieldConfig: { shape: 'object', fields: [{ name: 'twinType', label: 'Type', type: 'TEXT', required: true }] },
    validation: null, visible: true, value: null };
  render(<QuestionField question={question} onChange={vi.fn()} sessionId="s1" familyMemberId="fm1" />);
  fireEvent.click(screen.getByLabelText(/yes/i));
  expect(screen.getByLabelText('Type')).toBeInTheDocument();
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npx vitest run src/components/QuestionField.test.jsx` (from `patient-portal/`)
Expected: FAIL — no `STRUCTURED` branch yet.

- [ ] **Step 3: Implement**

1. `QuestionField.jsx`: accept `sessionId`/`familyMemberId` props; add `if (type === 'STRUCTURED') return <StructuredQuestionField question={question} sessionId={sessionId} excludeId={familyMemberId} onChange={onChange} />;` (placed alongside the other `type === '...'` branches, same style).
2. `TemplateView.jsx` (around line 101-105): add `sessionId={sessionId}` and `familyMemberId={familyMemberId}` to the existing `<QuestionField ... />` call.

- [ ] **Step 4: Run tests to verify they pass**

Run: `npx vitest run src/components/QuestionField.test.jsx src/components/TemplateView.test.jsx` (from `patient-portal/`)
Expected: PASS.

- [ ] **Step 5: Run the full frontend suite**

Run: `npx vitest run` and `npm run lint --silent` (from `patient-portal/`)
Expected: all tests pass, lint clean (the one pre-existing accepted `react(only-export-components)` warning on `FamilyMemberHub.jsx` is unaffected — do not try to fix it).

- [ ] **Step 6: Stage**

```bash
git add patient-portal/src/components/QuestionField.jsx patient-portal/src/components/TemplateView.jsx patient-portal/src/components/QuestionField.test.jsx
```

---

### Task 11: Reseed the 7 questions + fix `country_of_origin`, verified live

**Files:** none (dev-DB-only configuration, via the admin API — no repo files change).

**Interfaces:**
- Consumes: `PUT /api/v1/admin/question/{id}` (existing `QuestionController`, now accepting `fieldConfig` and `STRUCTURED` per Tasks 1-2).

- [ ] **Step 1: Start the backend**

Run (background): `cd QP && ./mvnw.cmd spring-boot:run -Dspring-boot.run.arguments=--server.port=8081 -Dmaven.test.skip=true`
Expected: starts cleanly, migration `029` applies (confirm via Liquibase log, same way `028` was confirmed earlier this session).

- [ ] **Step 2: Look up the 7 questions' current ids and the `country_of_origin` question id**

```bash
PGPASSWORD=questionnaire_app_pw "/c/Program Files/PostgreSQL/14/bin/psql.exe" -U questionnaire_app -d questionnaire_platform_final -c "SELECT id, answer_key FROM question WHERE answer_key IN ('diseases','genetic_findings','gender_transition','twinship','adoption','pregnancy_outcome','assisted_reproduction','country_of_origin');"
```

- [ ] **Step 3: `PUT` each of the 7 questions** with `type: "STRUCTURED"`, `validation: null`, and the exact `field_config` JSON from spec §5 (copy verbatim — do not retype by hand from memory), one `curl -X PUT .../api/v1/admin/question/{id}` per question, body `{"text": <unchanged existing text>, "type": "STRUCTURED", "displayOrder": <unchanged existing displayOrder>, "options": null, "validation": null, "visibilityRule": <unchanged existing visibilityRule>, "fieldConfig": {...}}` (look up each question's current `text`/`displayOrder`/`visibilityRule` via `GET /api/v1/admin/question/{id}` first, since `PUT` replaces the whole row — same care as every other admin-API edit this session).

- [ ] **Step 4: `PUT` `country_of_origin`** to `type: "CHECKBOX"` with `options` = the full ISO 3166-1 alpha-2 country list (builder's call per spec §8 on the exact literal source — a static array is fine, this repo has no existing country-list constant to reuse).

- [ ] **Step 5: Verify via `GET`**

```bash
curl -s http://localhost:8081/api/v1/admin/question/<id> | python -m json.tool
```
for each of the 8 reconfigured questions — confirm `type` and `fieldConfig`/`options` match exactly what was sent.

- [ ] **Step 6: Live smoke test**

Start the frontend dev server, open a session, drive to each of the 7 reconfigured templates (`GENDER_TRANSITION`, `CONDITIONS`, `GENETIC_TESTING`, `TWINSHIP`, `ADOPTION`, `PREGNANCY_OUTCOME`, `ASSISTED_REPRODUCTION`) plus the `DEMOGRAPHICS` country-of-origin field, submit one real answer on each, and confirm via a follow-up `GET current-template` (or the pedigree export) that the value round-trips. Report any mismatch before considering this task done — do not mark it complete from the automated tests alone, since this task's entire point is proving the real admin-configured data works end to end.

No `git add` — this task changes only the dev database, not the repository.
