# Rule Format Reference — JSONLogic

Every `templateRule` and `visibilityRule` in this platform is a **JSONLogic** expression, evaluated by `engine/RuleEngine` (`io.github.jamsesso:json-logic-java`, the same library chosen in `rule-engine-poc`). This is the only format the Rule Engine understands — there is no other rule syntax anywhere in the system.

---

## The mistake this doc exists to prevent

Three pieces of test data in the dev DB were created using a different, invented format instead of JSONLogic:

```json
// WRONG — not JSONLogic, the Rule Engine cannot evaluate this
{ "condition": "GENDER", "operator": "EQUALS", "value": "FEMALE" }
{ "condition": "ALWAYS" }
{ "condition": "NEVER" }
```

Calling `next-template` against a rule like this throws — `JsonLogic.apply()` doesn't recognize `condition`/`operator`/`value` as operators, so it fails to parse and the Rule Engine wraps that into a `RuleEvaluationException` (`500`). This has now been fixed in the dev DB (`WOMEN_DETAILS`, `PERSONAL_INFO`, `first_name`) — this doc is so it doesn't happen again.

The correct JSONLogic for the same three rules:

```json
// RIGHT
{ "==": [ { "var": "gender_at_birth" }, "FEMALE" ] }
null
null
```

`"ALWAYS"` and `"NEVER"` aren't rules at all — per FR-2/FR-10, a question or template with **no rule** (`null`) is unconditionally shown/loaded. Don't invent an always-true expression; just omit the rule.

---

## The shape

A JSONLogic expression is always a JSON object with **exactly one key** — the operator — whose value is the operator's arguments:

```json
{ "<operator>": [ <arg1>, <arg2>, ... ] }
```

To read a value from the current facts (the business entity — `PERSON` or `FAMILY_MEMBER` — as mapped by `PersonFactsMapper`), use `var`:

```json
{ "var": "answer_key_name" }
```

The name inside `var` is the `answer_key` the value was collected under (snake_case, matching the entity's column name — e.g. `gender_at_birth`, `birth_date`, `marital_status`), **not** the question's UUID and **not** a display label.

---

## Operators actually available (verified against the library, not assumed)

| Operator | JSONLogic | Meaning |
|---|---|---|
| Equals | `{"==": [a, b]}` | `a` equals `b` |
| Not equals | `{"!=": [a, b]}` | `a` does not equal `b` |
| Greater than | `{">": [a, b]}` | `a > b` |
| Greater than or equal | `{">=": [a, b]}` | `a >= b` |
| Less than | `{"<": [a, b]}` | `a < b` |
| Less than or equal | `{"<=": [a, b]}` | `a <= b` |
| Logical AND | `{"and": [cond1, cond2, ...]}` | all conditions true |
| Logical OR | `{"or": [cond1, cond2, ...]}` | any condition true |
| Logical NOT | `{"!": condition}` | negation |
| Truthy / exists | `{"!!": [{"var": "key"}]}` | `key` has been answered / is non-empty (used on `nickname` in `BASIC_DETAILS`) |
| Membership | `{"in": [value, {"var": "list_key"}]}` | `value` is one of the items in the array at `list_key` |

**Behavior worth knowing** (from `rule-engine-poc/OPERATORS.md`, actually run, not assumed):
- A **missing or not-yet-answered** `var` resolves as `null` — comparisons against it evaluate to `false`, they don't throw.
- A **type mismatch** (e.g. comparing a string fact to a number with `>=`) is **permissive** — it silently evaluates to `false`, it does not throw. Malformed *syntax* (unknown operator, broken JSON shape) is the only thing that throws.
- Nested facts use a dot-path: `{"var": "address.city"}`.

---

## Worked examples

**Template rule** — only load `WOMEN_DETAILS` if the proband's recorded sex is female:
```json
{ "==": [ { "var": "gender_at_birth" }, "FEMALE" ] }
```

**Template rule** — only load if age is over 18:
```json
{ ">": [ { "var": "age" }, 18 ] }
```

**Question visibility rule** — show a question only if an earlier one was answered:
```json
{ "!!": [ { "var": "first_name" } ] }
```

**Combining conditions** — load only if female **and** over 18:
```json
{
  "and": [
    { "==": [ { "var": "gender_at_birth" }, "FEMALE" ] },
    { ">": [ { "var": "age" }, 18 ] }
  ]
}
```

**No rule at all** — always show / always load (the correct way to express "ALWAYS"):
```json
null
```

---

## Two rules to remember when authoring a new rule

1. **Only one rule type exists**: `visibility_rule` on a question, `template_rule` on a template. There is no "disable" rule anymore (removed 2026-09-21 — see `QP-SRS.md` FR-10).
2. **A rule may only reference an *earlier* template's `answer_key`s, never its own** (FR-11) — e.g. a rule inside `WOMEN_DETAILS` (displayOrder 2) may reference anything from `BASIC_DETAILS` (displayOrder 1), never from `WOMEN_DETAILS` itself. This isn't checked automatically — it's an authoring convention you're responsible for following.
