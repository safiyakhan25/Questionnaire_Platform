# Untitled UI Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign patient-portal's entire UI using Untitled UI React's free, open-source component library (https://www.untitledui.com/react), unmodified default theme, for a production-quality look and genuinely better navigation.

**Architecture:** Untitled UI's components are real source files (`.tsx`, React Aria-based), copied verbatim into the repo under their own `src/components/{base,application,foundations}/...` convention — never hand-rolled lookalikes. This requires migrating from Tailwind v3 to v4 (Untitled UI's CSS-first config) and introducing TypeScript as a file-level capability (existing app code stays `.jsx`; only Untitled UI's own files and a handful of small infrastructure files are `.ts`/`.tsx` — Vite/esbuild transpiles both side by side with no extra tooling). A new persistent header gives the app its first consistent navigation chrome; every existing page/component is then re-skinned in place, swapping raw HTML form elements and ad-hoc divs for the real Untitled UI primitives while preserving existing behavior (and therefore most existing tests, which query by role/label/text, not CSS class).

**Tech Stack:** Tailwind CSS v4.3, `@tailwindcss/vite`, React Aria Components, Untitled UI React (free tier — confirmed by running `npx untitledui@latest init` into a throwaway directory and inspecting its real output; see Task 1), TypeScript (component-layer only), on top of the existing Vite 6 / React 19.3.1 / react-router-dom v7 / Vitest + Testing Library stack.

**Spec:** none — user explicitly opted out of a written design ("start implementing"). This plan's header sections carry the decisions a spec would normally hold.

## Global Constraints

- Every UI primitive (button, input, select, checkbox, radio, toggle, modal, badge, avatar, tabs, tooltip, nav, progress bar, empty state, loading indicator, date picker, tag-select) must be the real Untitled UI source, copied in by file, not a custom-styled lookalike. Composing several Untitled UI primitives together to cover a case no single component handles (e.g. a repeatable form-row "card" built from a plain `<div>` plus real `Button`/`Input` children) is fine — the primitives inside it must still be real.
- Untitled UI's default theme (`theme.css`, `typography.css`) is copied **verbatim, never edited** — this is what "don't change any colors" means in practice. No task may touch a value inside either file.
- `react-router-dom` (already in use, v7) stays the router. Untitled UI's own starter scaffold uses the `react-router` package (v8) instead — do not install or switch to it; wire Untitled UI's nav components to the existing router the same way the rest of the app already does (`<Link>`/`useNavigate` from `react-router-dom`).
- Existing app code stays `.jsx`. Only Untitled UI's own copied files, and the small infrastructure files that ship alongside them (`cx.ts`, `theme-provider.tsx`, etc.), are `.ts`/`.tsx`.
- No git commit or push at any step — stage with `git add` at most. The user commits and pushes herself.
- No new dependency beyond what an actually-used Untitled UI component needs (skip carousel/charts/QR-code/payment-icon/file-upload packages — nothing in this app uses them).
- Follow TDD (superpowers:test-driven-development) for genuinely new/changed behavior. For a pure re-skin (same behavior, different markup), the existing test for that file is the regression gate: it must still pass, updated only where a real DOM/role change makes the old assertion describe something that no longer exists (ledger every such change, same convention as this repo's two prior plans).

## Review Focus

- **Re-skinning a field silently changes its validated/submitted value shape** (e.g. `NativeSelect`'s `onChange` gives an event vs. a raw value some existing callers expect) — every swapped form field must still produce the exact same value to `onChange` the old one did. Covered per-field in Tasks 6-8.
- **A component copied from the scaffold references another Untitled UI file this app never copied in** (e.g. `Select` imports `popover.tsx`) — an incomplete copy silently breaks at runtime, not at copy time. Covered by Task 2's own completion contract (dev server boot + full suite, not just "files exist").
- **Dark mode activates unexpectedly** — `ThemeProvider` defaults to `"system"` and toggles a `dark-mode` class off `prefers-color-scheme`; a reviewer on a dark-mode OS seeing an unrequested dark UI could read as a bug. This is Untitled UI's own default behavior, left alone per the "don't change the theme" constraint — but it's non-obvious, so Task 1 states it explicitly rather than leaving a future reader to discover it by accident.
- **The existing `react-router-dom` Link/navigate calls stop working once Untitled UI nav components are wired in** — their own starter uses a different router package; Task 3 must prove navigation between real routes still works, not just that the header renders.
- **A `.tsx` file fails to compile/transpile when imported from a `.jsx` test file** — the very first thing Task 1 must prove, before any real page is touched, is that this mixed-language import actually works in this toolchain (dev server + Vitest + a trivial component).

---

## File Structure

New, copied verbatim from the harvested Untitled UI scaffold (paths below are the harvested scaffold's own paths under `src/`, copied to the same relative path under `patient-portal/src/`):
- `styles/theme.css`, `styles/typography.css`, `styles/globals.css` (trimmed of the one `recharts`-specific CSS rule — unused here)
- `utils/cx.ts`, `utils/is-react-component.ts`
- `hooks/use-breakpoint.ts`, `hooks/use-clipboard.ts`
- `providers/theme-provider.tsx`
- `components/base/buttons/button.tsx` (+ `close-button.tsx` if a redesigned modal needs it)
- `components/base/input/{input.tsx,label.tsx,hint-text.tsx}`
- `components/base/textarea/textarea.tsx`
- `components/base/select/{select.tsx,select-native.tsx,select-item.tsx,select-shared.tsx,popover.tsx,multi-select.tsx,tag-select.tsx,combobox.tsx}`
- `components/base/checkbox/checkbox.tsx`
- `components/base/radio-buttons/radio-buttons.tsx`
- `components/base/toggle/toggle.tsx`
- `components/base/badges/badges.tsx`
- `components/base/avatar/avatar.tsx`
- `components/base/tooltip/tooltip.tsx`
- `components/base/progress-indicators/progress-indicators.tsx`
- `components/base/form/form.tsx`
- `components/application/modals/modal.tsx`
- `components/application/app-navigation/{header-navigation.tsx,config.ts,base-components/*}`
- `components/application/app-navigation/sidebar-navigation/sidebar-simple.tsx`
- `components/application/empty-state/empty-state.tsx`
- `components/application/loading-indicator/loading-indicator.tsx`
- `components/application/date-picker/{date-picker.tsx,calendar.tsx,cell.tsx}`
- `components/application/tabs/tabs.tsx`
- `components/foundations/featured-icon/featured-icon.tsx` (used by empty-state)

New, written for this app:
- `patient-portal/src/components/layout/AppShell.jsx` + `.test.jsx` — the new persistent header wrapper.
- `patient-portal/tsconfig.json` — single file, no project-reference split (this app's `vite.config.js` stays plain JS, so no separate node-tsconfig is needed).

Modified (existing app files, re-skinned in place, no renames):
- `patient-portal/package.json`, `vite.config.js` — dependencies and the `@tailwindcss/vite` + `@` alias.
- `patient-portal/src/main.jsx` — import `styles/globals.css` instead of `index.css`; wrap in `ThemeProvider`.
- `patient-portal/src/App.jsx` — wrap routes in `AppShell`.
- `patient-portal/src/pages/SessionListPage.jsx`
- `patient-portal/src/components/TemplateView.jsx`, `VisitedTemplatesNav.jsx`, `FamilyMemberHub.jsx`
- `patient-portal/src/components/QuestionField.jsx`, `SimpleFields.jsx`
- `patient-portal/src/components/SearchableMultiSelect.jsx` → behavior absorbed into `TagSelect` usage (file likely deleted, see Task 7)
- `patient-portal/src/components/StructuredQuestionField.jsx`, `FieldRenderer.jsx`, `GroupListField.jsx`, `FamilyMemberSelect.jsx`
- `patient-portal/src/components/PedigreeViewerModal.jsx`
- `patient-portal/src/pages/CompletionPage.jsx`
- `patient-portal/src/components/OnboardingWalkthrough.jsx`

Removed: `patient-portal/tailwind.config.js`, `patient-portal/postcss.config.js`, `patient-portal/src/index.css` (whatever plain Tailwind-v3 base stylesheet main.jsx currently imports — confirm its name when you read `main.jsx` in Task 1).

---

### Task 1: Tailwind v4 + Untitled UI toolchain foundation

**Files:**
- Modify: `patient-portal/package.json`, `patient-portal/vite.config.js`, `patient-portal/src/main.jsx`
- Create: `patient-portal/tsconfig.json`, `patient-portal/src/styles/{theme.css,typography.css,globals.css}`, `patient-portal/src/utils/cx.ts`, `patient-portal/src/providers/theme-provider.tsx`
- Remove: `patient-portal/tailwind.config.js`, `patient-portal/postcss.config.js`, and main.jsx's current plain CSS import target
- Test: `patient-portal/src/components/base/buttons/button.test.jsx` (new, minimal canary)

**Interfaces:**
- Consumes: nothing (first task).
- Produces: a working Tailwind v4 + TypeScript-capable toolchain; `cx` from `utils/cx.ts`; `ThemeProvider`/`useTheme` from `providers/theme-provider.tsx`; the `@` → `./src` path alias in both `vite.config.js` and `tsconfig.json`. Every later task's copied `.tsx` file and every re-skinned page consumes this.

- [ ] **Step 1: Harvest the real scaffold (if not already done this session)**

Run, in a throwaway directory (not inside this repo): `npx untitledui@latest init <dirname> --vite -y -c brand`. This is non-interactive (`-y`) and pins the brand color to Untitled UI's own default (`-c brand`) — it does not prompt and does not change the theme. Read `src/styles/theme.css`, `src/styles/typography.css`, `src/styles/globals.css`, `src/utils/cx.ts`, `src/providers/theme-provider.tsx`, `package.json`, `vite.config.ts`, `tsconfig.app.json` from its output. These are the actual files to copy from in every step below — this plan's prose summarizes them, it does not replace reading them.

- [ ] **Step 2: Add dependencies, remove the old Tailwind v3 toolchain**

In `patient-portal/package.json`: add `tailwindcss@^4.3.3`, `@tailwindcss/vite@^4.3.3`, `@tailwindcss/typography`, `tailwind-merge`, `tailwindcss-animate`, `tailwindcss-react-aria-components`, `react-aria-components`, `react-aria`, `react-stately`, `@react-aria/utils`, `@react-stately/utils`, `@internationalized/date`, `@untitledui/icons`, and devDependencies `typescript`, `@types/node`. Remove `autoprefixer` and the old `tailwindcss@^3.4.19` entry (v4 replaces it) — `postcss` itself can also go, since `@tailwindcss/vite` doesn't route through PostCSS. Run `npm install`.

- [ ] **Step 3: Replace the Tailwind config and global stylesheet**

Delete `tailwind.config.js` and `postcss.config.js`. Copy `theme.css` and `typography.css` from the harvested scaffold into `patient-portal/src/styles/` **unedited**. Copy `globals.css` too, but drop its `.recharts-surface` rule (nothing in this app uses charts) — every other line stays as shipped. Read `patient-portal/src/main.jsx` to find its current plain-CSS import (e.g. `index.css`) and delete that file; `main.jsx`'s import now points at `@/styles/globals.css` instead (see Step 5 for the `@` alias).

- [ ] **Step 4: Add the Vite plugin and path alias**

In `vite.config.js`: import `@tailwindcss/vite` and add it to `plugins` alongside the existing `@vitejs/plugin-react()`; add `resolve.alias['@'] = path.resolve(__dirname, './src')` (mirrors the harvested `vite.config.ts` exactly, translated to this file's existing `.js` + whatever import style it already uses — read the current file first).

- [ ] **Step 5: Add TypeScript support**

Create `patient-portal/tsconfig.json` with the harvested `tsconfig.app.json`'s `compilerOptions` (target/module/jsx/strict/etc.) plus `"paths": {"@/*": ["./src/*"]}`, and `"include": ["src"]`. One file is enough here — this app's `vite.config.js` stays plain `.js`, so the harvested setup's separate `tsconfig.node.json` isn't needed. Copy `utils/cx.ts` and `providers/theme-provider.tsx` from the scaffold unedited.

- [ ] **Step 6: Wire `main.jsx`**

Change its stylesheet import to `@/styles/globals.css` and wrap the existing `<App />` tree in `<ThemeProvider>` (import from `@/providers/theme-provider`), passing no props — Untitled UI's own default (`defaultTheme="system"`) is the "unmodified theme" choice, left as-is.

- [ ] **Step 7: Write the canary test proving the toolchain actually works**

Copy `components/base/buttons/button.tsx` from the scaffold into `patient-portal/src/components/base/buttons/button.tsx` unedited (this is also the real first file of Task 2's component set — doing it here just proves the pipeline). Write `patient-portal/src/components/base/buttons/button.test.jsx`:

```jsx
import { render, screen } from '@testing-library/react';
import { Button } from './button';

it('renders its children', () => {
  render(<Button>Click me</Button>);
  expect(screen.getByRole('button', { name: 'Click me' })).toBeInTheDocument();
});
```

This is a `.jsx` test file importing a `.tsx` component — exactly the cross-language import every later task depends on.

- [ ] **Step 8: Run it, and the whole existing suite, and boot the dev server**

Run: `cd patient-portal && npx vitest run src/components/base/buttons/button.test.jsx`
Expected: PASS, 1/1 — proves Tailwind v4 compiles, the `.tsx` import works, and React Aria's underlying DOM renders a real `role="button"`.

Run: `npx vitest run`
Expected: all pre-existing tests still pass (103/103 before this task) — the config swap alone must not break anything else, since no other file has been touched yet.

Run: `npm run dev` (background), then `curl -s -o /dev/null -w "%{http_code}" http://localhost:5173/`
Expected: `200`. Stop the dev server afterward.

- [ ] **Step 9: Stage**

```bash
git add patient-portal/package.json patient-portal/package-lock.json patient-portal/vite.config.js \
        patient-portal/tsconfig.json patient-portal/src/main.jsx \
        patient-portal/src/styles/theme.css patient-portal/src/styles/typography.css patient-portal/src/styles/globals.css \
        patient-portal/src/utils/cx.ts patient-portal/src/providers/theme-provider.tsx \
        patient-portal/src/components/base/buttons/button.tsx patient-portal/src/components/base/buttons/button.test.jsx
git add -u patient-portal/tailwind.config.js patient-portal/postcss.config.js
```

(the `tailwind.config.js`/`postcss.config.js` line stages their deletion; also stage the deletion of whatever old CSS file `main.jsx` used to import, under its own real name from Step 3.)

---

### Task 2: Copy in the full Untitled UI component set

**Files:**
- Create: every file listed under "New, copied verbatim" in the File Structure section above, except `button.tsx` (already copied in Task 1).

**Interfaces:**
- Consumes: Task 1's toolchain (`cx` from `utils/cx.ts`, Tailwind v4 tokens from `styles/theme.css`).
- Produces: every component name confirmed by this plan's own research and listed in the table below — these exact names are what every later task imports. Where a name isn't in the table, the owning task's own step says to grep the real file for its `export` lines before using it (don't guess).

| File | Confirmed exports |
|---|---|
| `base/input/input.tsx` | `Input`, `TextField`, `InputBase` |
| `base/textarea/textarea.tsx` | `TextArea` |
| `base/select/select.tsx` | `Select` |
| `base/select/select-native.tsx` | `NativeSelect` |
| `base/select/multi-select.tsx` | `MultiSelect` |
| `base/select/tag-select.tsx` | `TagSelect` |
| `base/checkbox/checkbox.tsx` | `Checkbox`, `CheckboxBase` |
| `base/radio-buttons/radio-buttons.tsx` | `RadioButton`, `RadioGroup` |
| `base/toggle/toggle.tsx` | `Toggle` |
| `base/badges/badges.tsx` | `Badge`, `BadgeWithDot`, `BadgeWithIcon` (+ others, unused here) |
| `base/avatar/avatar.tsx` | `Avatar` |
| `base/tooltip/tooltip.tsx` | `Tooltip`, `TooltipTrigger` |
| `base/progress-indicators/progress-indicators.tsx` | `ProgressBar`, `ProgressBarBase` |
| `application/modals/modal.tsx` | `ModalOverlay`, `Modal`, `Dialog`, `DialogTrigger` |
| `application/app-navigation/header-navigation.tsx` | `HeaderNavigationBase` |
| `application/app-navigation/sidebar-navigation/sidebar-simple.tsx` | `SidebarNavigationSimple` |
| `application/empty-state/empty-state.tsx` | `EmptyState` |
| `application/loading-indicator/loading-indicator.tsx` | `LoadingIndicator` |
| `application/date-picker/date-picker.tsx` | `DatePicker` |
| `application/tabs/tabs.tsx` | `Tabs`, `TabList`, `TabPanel`, `Tab` |

No free-tier rejection exists to check for — Task 1's harvested `init` scaffold already proved every one of these files is included by default, with no PRO prompt at any point.

- [ ] **Step 1: Copy every file in the File Structure section's "New, copied verbatim" list (minus `button.tsx`)**

Copy each file byte-for-byte from the harvested scaffold to the same relative path under `patient-portal/src/`. Do not edit their contents — any prop/behavior adjustment belongs in the app code that *uses* them, in later tasks, never in the library files themselves.

- [ ] **Step 2: Resolve cross-file imports**

Some copied files import siblings not yet listed (e.g. `select.tsx` imports `popover.tsx`, `select-shared.tsx`, `select-item.tsx` — already included above; `empty-state.tsx` likely imports `featured-icon.tsx` — included above). Grep each newly-copied file for `from "@/` imports and confirm every target is either already copied or copy it too. This is the Review Focus item about an incomplete copy failing silently at runtime, not at copy time.

- [ ] **Step 3: Verify the whole set compiles and nothing existing broke**

Run: `cd patient-portal && npx vitest run`
Expected: still 104/104 (103 pre-existing + Task 1's canary) — copying unused files shouldn't change any existing test, but this is the cheapest way to catch an import-resolution failure across the whole new tree.

Run: `npm run dev` (background) → `curl -s -o /dev/null -w "%{http_code}" http://localhost:5173/` → `200`. Stop it.

Run: `npm run lint`
Expected: clean except the 3 pre-existing accepted warnings — oxlint should still run cleanly over the new `.tsx` files (if it errors on `.tsx` syntax it doesn't understand, note that as a ruling: lint scope may need to exclude `src/components/base`/`src/components/application`/`src/components/foundations`, since those are vendored library code, not this app's own source, the same way a `node_modules` tree would never be linted).

- [ ] **Step 4: Stage**

```bash
git add patient-portal/src/components/base patient-portal/src/components/application patient-portal/src/components/foundations \
        patient-portal/src/hooks patient-portal/src/utils/is-react-component.ts
```

---

### Task 3: Persistent app shell (header navigation)

**Files:**
- Create: `patient-portal/src/components/layout/AppShell.jsx`, `patient-portal/src/components/layout/AppShell.test.jsx`
- Modify: `patient-portal/src/App.jsx`

**Interfaces:**
- Consumes: `HeaderNavigationBase` from Task 2 (read its actual prop signature from the real file before writing `AppShell` — this plan does not pin its exact props, since the research behind this plan never confirmed them with certainty).
- Produces: `export default function AppShell({ children })` — wraps page content with a persistent top header. Task 1's `App.jsx` routes render inside it from this task forward; every later page-redesign task assumes this header is already present above its own content.

- [ ] **Step 1: Read `header-navigation.tsx` in full** to get its real prop shape (items array? just children? a title prop?).

- [ ] **Step 2: Write the failing test**

```jsx
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import AppShell from './AppShell';

it('renders a persistent header and its children', () => {
  render(<MemoryRouter><AppShell><p>Page content</p></AppShell></MemoryRouter>);
  expect(screen.getByRole('banner')).toBeInTheDocument(); // <header> landmark
  expect(screen.getByText('Page content')).toBeInTheDocument();
});
```

(`MemoryRouter` wraps the test because `HeaderNavigationBase` likely renders a `react-router-dom` `<Link>` for the brand/logo — confirm against what Step 1 found, adjust the test if it turns out to need no router context at all.)

- [ ] **Step 3: Run to verify it fails**

Run: `npx vitest run src/components/layout/AppShell.test.jsx`
Expected: FAIL — module doesn't exist.

- [ ] **Step 4: Implement `AppShell`**

Compose `HeaderNavigationBase` (or, if Step 1 finds it too dashboard-specific for this app's single-brand, no-user-menu case, a minimal header built from real Untitled UI pieces only — e.g. just the logo/title slot it exposes, with no nav items array) with "QuestionnairePlatform" as the brand/title text, and render `{children}` below it.

- [ ] **Step 5: Run to verify it passes**

Run: `npx vitest run src/components/layout/AppShell.test.jsx`
Expected: PASS, 1/1.

- [ ] **Step 6: Wrap the app**

In `App.jsx`, wrap `<AppRoutes />` in `<AppShell>`.

- [ ] **Step 7: Run the full suite**

Run: `npx vitest run`
Expected: all pass (check `App.test.jsx` specifically — it renders real routes and may now also need `getByRole('banner')`-adjacent assertions updated only if the header's presence collides with an existing query, e.g. if any existing test used `getByText` for something the header now also contains).

- [ ] **Step 8: Stage**

```bash
git add patient-portal/src/components/layout/AppShell.jsx patient-portal/src/components/layout/AppShell.test.jsx patient-portal/src/App.jsx
```

---

### Task 4: SessionListPage redesign

**Files:**
- Modify: `patient-portal/src/pages/SessionListPage.jsx`, `patient-portal/src/pages/SessionListPage.test.jsx` (only if a role/text genuinely changes)

**Interfaces:**
- Consumes: `Button` (Task 1/2), `Badge` (Task 2), `EmptyState` (Task 2).

- [ ] **Step 1: Read `SessionListPage.jsx` and its test in full.**

- [ ] **Step 2: Redesign**

Replace the plain `<button>` "Start new session" with Untitled UI's `Button` (`color="primary"`). Replace the bare `<ul>` of text links with a styled list — each row a session's display name plus a `Badge` showing its status (`IN_PROGRESS`/`COMPLETED`, mapped to a sensible `Badge` color from the ones Task 2's file exports — not inventing a new color, reading what's actually available). When `sessions` is empty, render `EmptyState` instead of a bare empty `<ul>`. Preserve every existing `onClick`/navigation behavior exactly — this task changes markup and styling only.

- [ ] **Step 3: Run the existing test suite for this file**

Run: `npx vitest run src/pages/SessionListPage.test.jsx`
Expected: passes unchanged, since the test queries by button/link text, not markup — if it fails, the failure must be about an actual behavior change (fix the component, don't adjust the test to hide it) unless the failure is a genuine, intentional role change (e.g. a session row stops being a `<button>` and becomes something `Badge`-wrapped that changes its accessible role) — in that case, ledger the change.

- [ ] **Step 4: Full suite + lint**

Run: `npx vitest run` and `npm run lint` — both clean.

- [ ] **Step 5: Stage**

```bash
git add patient-portal/src/pages/SessionListPage.jsx patient-portal/src/pages/SessionListPage.test.jsx
```

---

### Task 5: TemplateView shell, VisitedTemplatesNav, FamilyMemberHub

**Files:**
- Modify: `patient-portal/src/components/TemplateView.jsx`, `TemplateView.test.jsx`, `VisitedTemplatesNav.jsx`, `VisitedTemplatesNav.test.jsx`, `FamilyMemberHub.jsx`, `FamilyMemberHub.test.jsx`

**Interfaces:**
- Consumes: `Button`, `NativeSelect` (Task 2), `Badge` (Task 2), `ProgressBar` (Task 2), `SidebarNavigationSimple` or a simpler styled list if that component's `items` API proves too heavy for a dynamically-growing visited-templates list (read its real file first; a plain styled `<nav>`/`<ul>` using the same color tokens is an acceptable fallback here specifically, since `SidebarNavigationSimple` is built around a fixed nav-item config, not a list that grows as the user answers questions — ledger whichever choice is made and why).

**This is the largest single task in the plan — TemplateView owns the sticky sidebar/divider layout, the Pedigree Viewer button, the family-member banner, the `FAMILY_MEMBER_HUB` branch, and the completed-browse branch, all of which must keep working exactly as today, just re-skinned.**

- [ ] **Step 1: Read all three files and their tests in full, fresh** (this plan's earlier summary of TemplateView is from memory of an earlier session turn — the real current file is the only source of truth for exact prop names/branches).

- [ ] **Step 2: Redesign `VisitedTemplatesNav`**

Re-skin its list using the color tokens/typography classes from `theme.css`/`typography.css` (`text-sm`, `font-medium`, etc. already used via Tailwind utility names that exist in both v3 and v4) and `Button` (`color="link-color"` or similar, per whatever link-style variant Task 1's `button.tsx` actually exports) for each clickable template name, in place of the current raw `<button className="text-blue-600 underline...">`. Preserve the exact `onClick={() => handleClick(t.templateId)}` wiring.

- [ ] **Step 3: Redesign `FamilyMemberHub`**

Replace the three `<select>` elements (relationship, side, "related to") with `NativeSelect` (keeps native `<select>` DOM semantics, so existing `fireEvent.change(screen.getByLabelText(...), {target:{value}})` tests keep working unchanged) and the submit `<button>` with `Button`. Style the "Continue as X" rows and the family-member list similarly to Task 4's session list.

- [ ] **Step 4: Redesign `TemplateView`'s own shell**

Replace the plain "Next"/"Pedigree Viewer" buttons with `Button`. Replace the "you are now answering for" banner `<div>` with a styled panel using theme tokens (and `Badge` for the subject name, if that reads better once seen). Add a `ProgressBar` showing how many templates this subject has visited vs. however many are reachable — **a new, additive bit of UI, not present today**; since it's new behavior, write a failing test for it first (does the DOM contain a progress element reflecting the right value) before implementing, per standard TDD. Keep the sticky-sidebar-with-divider layout structurally the same (left column + right content), just re-skinned with Untitled UI's own border/spacing tokens instead of hand-picked Tailwind v3 utility values.

- [ ] **Step 5: Run each file's own test suite, fixing only what Step 1's real read says must change**

Run: `npx vitest run src/components/VisitedTemplatesNav.test.jsx src/components/FamilyMemberHub.test.jsx src/components/TemplateView.test.jsx`
Expected: all pass; any new test (the `ProgressBar` one) goes RED → implement → GREEN first, before this run.

- [ ] **Step 6: Full suite + lint**

Run: `npx vitest run` (watch specifically for the `FAMILY_MEMBER_HUB` and `onFamilyMemberPassEnded` tests noted in this repo's own Review Focus precedent from the pedigree-viewer plan — they must still pass) and `npm run lint`.

- [ ] **Step 7: Stage**

```bash
git add patient-portal/src/components/TemplateView.jsx patient-portal/src/components/TemplateView.test.jsx \
        patient-portal/src/components/VisitedTemplatesNav.jsx patient-portal/src/components/VisitedTemplatesNav.test.jsx \
        patient-portal/src/components/FamilyMemberHub.jsx patient-portal/src/components/FamilyMemberHub.test.jsx
```

---

### Task 6: Simple form fields (QuestionField + SimpleFields)

**Files:**
- Modify: `patient-portal/src/components/SimpleFields.jsx`, `SimpleFields.test.jsx`, `QuestionField.jsx`, `QuestionField.test.jsx`

**Interfaces:**
- Consumes: `Input`, `TextArea`, `DatePicker`, `NativeSelect`, `Checkbox` (Task 2).
- Produces: `TextField`/`NumberField`/`DateField`/`DropdownField` keep their existing names/props (`fieldId, label, value, required, validation, onChange`) so `QuestionField.jsx` doesn't need its own call sites touched beyond the `CHECKBOX` branch — only their internal implementation changes.

- [ ] **Step 1: Read `SimpleFields.jsx` and `QuestionField.jsx` in full, fresh.**

- [ ] **Step 2: Redesign `TextField`/`NumberField`**

Swap the raw `<input>` for Untitled UI's `Input`, keeping the exact same `onChange(value)` contract (`Input`'s own `onChange` likely hands back an event or a raw string — read `input.tsx`'s real signature in Task 2's already-copied file and adapt the wrapper so `TextField`/`NumberField` still call the parent's `onChange` with a plain string/number, exactly as today).

- [ ] **Step 3: Redesign `DateField`**

Decide, after reading `date-picker.tsx`'s real value type (likely `@internationalized/date`'s `CalendarDate`, not a plain ISO string): either (a) adopt `DatePicker` properly, converting to/from the ISO `yyyy-mm-dd` string this app stores everywhere (`parseDate`/`.toString()` from `@internationalized/date`), or (b) if that conversion would touch every DOB-adjacent consumer this task doesn't otherwise need to touch (the age-calculation code in `QuestionField.jsx`, which already expects a plain string), keep a styled native `<input type="date">` for now and ledger that `DatePicker` adoption is deferred. Prefer (a) if the conversion is contained to `DateField` itself; make the call and ledger it either way.

- [ ] **Step 4: Redesign `DropdownField`**

Swap for `NativeSelect` — same native-select DOM semantics as today, so the existing `fireEvent.change` tests should need no changes.

- [ ] **Step 5: Redesign `QuestionField`'s `CHECKBOX` branch**

Swap each raw `<input type="checkbox">` for Untitled UI's `Checkbox`, preserving the exact `selected`/`toggle` array-of-values contract already there.

- [ ] **Step 6: Run both files' test suites**

Run: `npx vitest run src/components/SimpleFields.test.jsx src/components/QuestionField.test.jsx`
Expected: pass, with only the minimal, ledgered changes a genuine DOM-role change requires.

- [ ] **Step 7: Full suite + lint**, same as prior tasks.

- [ ] **Step 8: Stage**

```bash
git add patient-portal/src/components/SimpleFields.jsx patient-portal/src/components/SimpleFields.test.jsx \
        patient-portal/src/components/QuestionField.jsx patient-portal/src/components/QuestionField.test.jsx
```

---

### Task 7: Country-of-origin picker → `TagSelect`

**Files:**
- Modify: `patient-portal/src/components/QuestionField.jsx` (the `country_of_origin` branch)
- Delete: `patient-portal/src/components/SearchableMultiSelect.jsx`, `SearchableMultiSelect.test.jsx` (fully superseded)

**Interfaces:**
- Consumes: `TagSelect` (Task 2) — confirmed to exist and to be built for exactly this "search, select from options, show removable tags" pattern, so no custom chip-row code is needed at all.

- [ ] **Step 1: Read `tag-select.tsx` in full** to get its real prop names (options shape, value shape, `onChange` signature).

- [ ] **Step 2: Write the failing test** (replacing `SearchableMultiSelect.test.jsx`'s cases, adapted to `TagSelect`'s real API found in Step 1) directly in `QuestionField.test.jsx`'s existing `country_of_origin` test — same assertions as today (typing filters, selecting adds a value, a selected value shows removably, removing it updates the array) but through `TagSelect`'s real rendered markup, not `SearchableMultiSelect`'s custom one.

- [ ] **Step 3: Run to verify it fails**, implement the `country_of_origin` branch in `QuestionField.jsx` using `TagSelect` directly (no wrapper component needed, unless `TagSelect`'s prop shape genuinely doesn't fit inline — judgment call, keep it inline if it reasonably fits), delete `SearchableMultiSelect.jsx`/`.test.jsx`.

- [ ] **Step 4: Run to verify it passes.**

Run: `npx vitest run src/components/QuestionField.test.jsx`
Expected: PASS.

- [ ] **Step 5: Full suite + lint.**

- [ ] **Step 6: Stage**

```bash
git add patient-portal/src/components/QuestionField.jsx patient-portal/src/components/QuestionField.test.jsx
git add -u patient-portal/src/components/SearchableMultiSelect.jsx patient-portal/src/components/SearchableMultiSelect.test.jsx
```

---

### Task 8: Structured-question components

**Files:**
- Modify: `patient-portal/src/components/StructuredQuestionField.jsx`, `.test.jsx`, `FieldRenderer.jsx`, `.test.jsx`, `GroupListField.jsx`, `.test.jsx`, `FamilyMemberSelect.jsx`, `.test.jsx`

**Interfaces:**
- Consumes: `RadioGroup`/`RadioButton` or `Toggle` (Task 2, pick one for the Yes/No apply-gate — `RadioGroup` with two `RadioButton`s reads more like a real yes/no question in a clinical form than a single `Toggle` switch; make the call), `Button` (add/remove row), `NativeSelect`/`MultiSelect` (Task 2, for `FamilyMemberSelect`'s single/multi modes).

- [ ] **Step 1: Read all four files and their tests in full, fresh.**

- [ ] **Step 2: Redesign `StructuredQuestionField`'s Yes/No gate** using `RadioGroup`+`RadioButton` (or the alternative chosen in Step 1's judgment call), preserving the exact `applies`/`current` state logic already there.

- [ ] **Step 3: Redesign `GroupListField`'s repeatable rows** as a styled card (border/padding from theme tokens) per row, with `Button` for "Add"/"Remove" in place of the current underlined text buttons.

- [ ] **Step 4: Redesign `FamilyMemberSelect`** using `NativeSelect` (single) or `MultiSelect` (multi, per its existing `multiple` prop), preserving its existing `excludeId` filtering behavior exactly.

- [ ] **Step 5: `FieldRenderer`** only needs touching if it renders any markup of its own beyond dispatching to the above (read it first — it may need no change at all beyond passing through already-redesigned children).

- [ ] **Step 6: Run each file's own tests, then the full suite + lint.**

- [ ] **Step 7: Stage**

```bash
git add patient-portal/src/components/StructuredQuestionField.jsx patient-portal/src/components/StructuredQuestionField.test.jsx \
        patient-portal/src/components/FieldRenderer.jsx patient-portal/src/components/FieldRenderer.test.jsx \
        patient-portal/src/components/GroupListField.jsx patient-portal/src/components/GroupListField.test.jsx \
        patient-portal/src/components/FamilyMemberSelect.jsx patient-portal/src/components/FamilyMemberSelect.test.jsx
```

---

### Task 9: PedigreeViewerModal → real Modal/Dialog

**Files:**
- Modify: `patient-portal/src/components/PedigreeViewerModal.jsx`, `.test.jsx`

**Interfaces:**
- Consumes: `ModalOverlay`, `Modal`, `Dialog` (Task 2).

- [ ] **Step 1: Read `modal.tsx` in full** for its real prop shape (`ModalOverlay`'s `isOpen`/`onOpenChange` is the React-Aria-standard pattern per the research — confirm against the actual file) and `PedigreeViewerModal.jsx` fresh.

- [ ] **Step 2: Redesign**, keeping the exact loading/error/loaded states and the `onClose` contract the parent (`TemplateView`) already relies on — only the overlay/dialog chrome changes, not the fetch/object-URL lifecycle logic.

- [ ] **Step 3: Run its test suite**, updating only what a genuine role change requires (its current `role="dialog"` was hand-set — `Dialog` from Untitled UI should provide this natively, so that assertion likely needs no change at all).

Run: `npx vitest run src/components/PedigreeViewerModal.test.jsx`
Expected: PASS.

- [ ] **Step 4: Check `TemplateView.test.jsx`'s modal-related test still passes** (it asserts on `getByRole('dialog')` from Task 5's own changes — confirm the two tasks' assumptions about the dialog role still agree).

- [ ] **Step 5: Full suite + lint.**

- [ ] **Step 6: Stage**

```bash
git add patient-portal/src/components/PedigreeViewerModal.jsx patient-portal/src/components/PedigreeViewerModal.test.jsx
```

---

### Task 10: CompletionPage redesign

**Files:**
- Modify: `patient-portal/src/pages/CompletionPage.jsx`, `.test.jsx`

**Interfaces:**
- Consumes: `Button` (Task 2) for the SVG/PNG/PDF downloads and "Edit answers".

- [ ] **Step 1: Read `CompletionPage.jsx` and its test in full, fresh** (this file already has a bordered/centered pedigree card from a prior session's turn — re-skin it with real Untitled UI tokens/components, don't redesign its layout logic, which already works).

- [ ] **Step 2: Redesign** the download/edit button row with real `Button`s (`color="secondary"` for the format buttons, `color="link-color"` or similar for "Edit answers" — read `button.tsx`'s real variant names rather than guessing), keeping every existing `onClick`/`download`/`getPedigreeDiagram` call exactly as today.

- [ ] **Step 3: Run its test suite, then full suite + lint.**

- [ ] **Step 4: Stage**

```bash
git add patient-portal/src/pages/CompletionPage.jsx patient-portal/src/pages/CompletionPage.test.jsx
```

---

### Task 11: OnboardingWalkthrough → Modal-based walkthrough

**Files:**
- Modify: `patient-portal/src/components/OnboardingWalkthrough.jsx`, `.test.jsx`

**Interfaces:**
- Consumes: `ModalOverlay`/`Modal`/`Dialog` (Task 2, same as Task 9), `Button`, `ProgressBar` or a simple dot-row (read the file first to see how many steps exist today before deciding which progress treatment fits).

- [ ] **Step 1: Read `OnboardingWalkthrough.jsx` and its test in full, fresh.**

- [ ] **Step 2: Redesign**, preserving its exact step/dismiss logic and the `onDismiss` contract `AssessmentPage.jsx` already relies on.

- [ ] **Step 3: Run its test suite, then full suite + lint.**

- [ ] **Step 4: Stage**

```bash
git add patient-portal/src/components/OnboardingWalkthrough.jsx patient-portal/src/components/OnboardingWalkthrough.test.jsx
```

---

### Task 12: Full manual walkthrough against the real running app

**Files:** none — this task only looks, never edits.

**Interfaces:**
- Consumes: everything from Tasks 1-11.

Automated tests (Vitest + jsdom) never render real visual styling — a layout that's merely ugly passes every test. This task is the only check for that, and it is mandatory, not optional, for a plan whose entire point is "it looks good."

- [ ] **Step 1: Start the real stack**

Backend (`cd QP && ./mvnw.cmd spring-boot:run -Dspring-boot.run.arguments=--server.port=8081 -Dmaven.test.skip=true`, background), frontend (`cd patient-portal && npm run dev`, background), PedigreeDraw if the pedigree viewer is in scope to check (`PEDIGREE_HTTP_PORT=8082 java -jar .../pedigree-http-*-uber.jar`, background — already-built jar from this repo's prior plan).

- [ ] **Step 2: Look at every redesigned surface**

If `claude-in-chrome` browser tools are available in this session, use them to actually navigate and screenshot each of: the session list (empty and with sessions), starting a fresh assessment, a plain text/number/date/radio/dropdown/checkbox question, a STRUCTURED question (e.g. `twinship`), the country-of-origin `TagSelect`, the `FAMILY_MEMBER_HUB` screen and adding a family member, the Pedigree Viewer modal, completing an assessment, the completion page (including a download button), and the onboarding walkthrough on first visit (clear `localStorage`'s `qp_onboarding_seen` key first to see it). For each, confirm: Untitled UI's real components are visibly in use (not a plain unstyled HTML fallback), the brand purple/theme is consistent, nothing is visually broken (overlapping text, unstyled raw buttons, broken layout).

If `claude-in-chrome` is not available or not connected, do the same walkthrough via `curl` against the real backend (as this repo's prior two plans' final tasks did) to confirm every endpoint/flow still functions end-to-end, and say plainly in the final report that the purely-visual check could not be performed — don't claim "looks good" without having looked.

- [ ] **Step 3: Stop the background processes** (backend, frontend, PedigreeDraw if started).

No commit step.
