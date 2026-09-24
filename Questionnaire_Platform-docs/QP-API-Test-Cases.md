# API Test Cases — Workflow / Template / Question

Base path for every endpoint below: `/api/v1/admin`

---

## Workflow APIs

| API | Use | Status | Description |
|---|---|---|---|
| POST /workflows | Create with valid code, name, displayOrder | 201 | Workflow created, full body returned |
| POST /workflows | Missing code / name / displayOrder | 400 | Validation error, lists every missing field |
| POST /workflows | Duplicate `code` | 409 | A workflow with that code already exists |
| POST /workflows | Duplicate `displayOrder` | 409 | A workflow with that displayOrder already exists |
| GET /workflows | List all | 200 | Returns all workflows, ordered by displayOrder |
| GET /workflow/{id} | Id exists | 200 | Returns the workflow |
| GET /workflow/{id} | Id does not exist | 404 | Workflow not found |
| PUT /workflow/{id} | Valid update | 200 | name/description/displayOrder updated; code unchanged (immutable) |
| PUT /workflow/{id} | Missing name / displayOrder | 400 | Validation error |
| PUT /workflow/{id} | Id does not exist | 404 | Workflow not found |
| PUT /workflow/{id} | New displayOrder collides with another workflow | 409 | displayOrder already in use |
| PUT /workflow/{id} | displayOrder unchanged (its own current value) | 200 | Not falsely blocked as a duplicate |
| DELETE /workflow/{id} | No templates exist under it | 204 | Deleted |
| DELETE /workflow/{id} | Templates still exist under it | 409 | Blocked — delete/reassign templates first |
| DELETE /workflow/{id} | Id does not exist | 404 | Workflow not found |

---

## Template APIs

| API | Use | Status | Description |
|---|---|---|---|
| POST /workflow/{workflowId}/templates | Create with valid code, name, displayOrder (templateRule optional) | 201 | Template created; templateRule stored/returned as real nested JSON |
| POST /workflow/{workflowId}/templates | workflowId does not exist | 404 | Workflow not found |
| POST /workflow/{workflowId}/templates | Missing code / name / displayOrder | 400 | Validation error |
| POST /workflow/{workflowId}/templates | Duplicate `code` | 409 | A template with that code already exists |
| POST /workflow/{workflowId}/templates | Duplicate `displayOrder` within the same workflow | 409 | displayOrder already used in this workflow |
| POST /workflow/{workflowId}/templates | Same `displayOrder` used in a *different* workflow | 201 | Allowed — uniqueness is scoped per workflow, not global |
| GET /workflow/{workflowId}/templates | List templates for a workflow | 200 | Returns templates, ordered by displayOrder |
| GET /workflow/{workflowId}/templates | workflowId does not exist | 404 | Workflow not found |
| GET /template/{id} | Id exists | 200 | Returns the template |
| GET /template/{id} | Id does not exist | 404 | Template not found |
| PUT /template/{id} | Valid update | 200 | name/description/displayOrder/templateRule updated; code unchanged |
| PUT /template/{id} | Missing name / displayOrder | 400 | Validation error |
| PUT /template/{id} | Id does not exist | 404 | Template not found |
| PUT /template/{id} | New displayOrder collides with another template in the same workflow | 409 | displayOrder already in use in this workflow |
| PUT /template/{id} | displayOrder unchanged (its own current value) | 200 | Not falsely blocked as a duplicate |
| DELETE /template/{id} | No questions exist under it | 204 | Deleted |
| DELETE /template/{id} | Questions still exist under it | 409 | Blocked — delete/reassign questions first |
| DELETE /template/{id} | Id does not exist | 404 | Template not found |

---

## Question APIs

| API | Use | Status | Description |
|---|---|---|---|
| POST /template/{templateId}/questions | Create with valid answerKey, text, type, displayOrder (options/validation/visibilityRule optional) | 201 | Question created; JSON fields round-trip as real nested JSON |
| POST /template/{templateId}/questions | templateId does not exist | 404 | Template not found |
| POST /template/{templateId}/questions | Missing answerKey / text / type / displayOrder | 400 | Validation error |
| POST /template/{templateId}/questions | Invalid `type` value or malformed JSON body | 400 | "Malformed request body" |
| POST /template/{templateId}/questions | Duplicate `answerKey` | 409 | A question with that answerKey already exists |
| POST /template/{templateId}/questions | Duplicate `displayOrder` within the same template | 409 | displayOrder already used in this template |
| POST /template/{templateId}/questions | Same `displayOrder` used in a *different* template | 201 | Allowed — uniqueness is scoped per template, not global |
| GET /template/{templateId}/questions | List questions for a template | 200 | Returns questions, ordered by displayOrder |
| GET /template/{templateId}/questions | templateId does not exist | 404 | Template not found |
| GET /question/{id} | Id exists | 200 | Returns the question, including full JSONB payload |
| GET /question/{id} | Id does not exist | 404 | Question not found |
| PUT /question/{id} | Valid update | 200 | text/type/displayOrder/options/validation/visibilityRule updated; answerKey unchanged |
| PUT /question/{id} | Missing text / type / displayOrder | 400 | Validation error |
| PUT /question/{id} | Id does not exist | 404 | Question not found |
| PUT /question/{id} | New displayOrder collides with another question in the same template | 409 | displayOrder already in use in this template |
| PUT /question/{id} | displayOrder unchanged (its own current value) | 200 | Not falsely blocked as a duplicate |
| DELETE /question/{id} | No QUESTION_RESPONSE rows reference it | 204 | Deleted |
| DELETE /question/{id} | QUESTION_RESPONSE rows already reference it | 409 | Blocked — cannot delete a question with submitted answers |
| DELETE /question/{id} | Id does not exist | 404 | Question not found |

---

## Error shape (applies to every 4xx/409 above)

Every error response, from any endpoint, has the same structure:

```json
{
  "status": 409,
  "error": "Conflict",
  "message": "A workflow with code 'PERSONAL_DETAILS' already exists",
  "path": "/api/v1/admin/workflows",
  "timestamp": "2026-09-16T21:33:05.008"
}
```

## Not covered by these APIs

- No authentication/authorization on any endpoint (out of scope for this phase, per SRS)
- No pagination on the list endpoints
- No shape validation on `options` / `validation` / `templateRule` / `visibilityRule` beyond being valid JSON — nothing checks e.g. that a RADIO question actually has options
- FR-11 (a rule may only reference an earlier template, never its own) is not runtime-enforced — it's a documented authoring convention only
- `disable_rule` was removed from `QUESTION` entirely (2026-09-21) — a question now carries only `visibility_rule`. See `QP-SRS.md` FR-10/FR-4.
