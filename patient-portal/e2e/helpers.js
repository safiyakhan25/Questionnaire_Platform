// Generic, schema-agnostic questionnaire traversal: the real backend's template/question content
// can change at any time via the admin API (that's the whole point of this app), so this E2E
// suite must never hardcode a question's text, answer key, or option values - it only knows the
// rendered HTML shapes QuestionField produces for each QuestionType.

export async function fillAllVisibleFields(page) {
  // `:not([role="combobox"])` excludes the country-of-origin TagSelect's search box - it's a
  // `type="text"` input too, but typing arbitrary text into it only filters a dropdown list
  // without selecting anything, which would leave it looking "filled" while answering nothing.
  const textLike = page.locator(
    'input[type="text"]:visible:not([role="combobox"]), input[type="number"]:visible, input[type="date"]:visible',
  );
  const textLikeCount = await textLike.count();
  for (let i = 0; i < textLikeCount; i++) {
    const input = textLike.nth(i);
    if (await input.inputValue()) continue;
    const type = await input.getAttribute('type');
    if (type === 'number') await input.fill('1');
    else if (type === 'date') await input.fill('1990-01-01');
    else await input.fill('Test');
  }

  const selects = page.locator('select:visible');
  const selectCount = await selects.count();
  for (let i = 0; i < selectCount; i++) {
    const select = selects.nth(i);
    if (await select.inputValue()) continue;
    const firstRealOption = select.locator('option[value]:not([value=""])').first();
    if (await firstRealOption.count()) {
      await select.selectOption(await firstRealOption.getAttribute('value'));
    }
  }

  // Untitled UI's RadioButton/Checkbox (react-aria-components under them) render their real native
  // input visually-hidden, with a styled decorative box drawn on top for the visible appearance -
  // so these can never be found via `:visible`. `.check()` also doesn't work even with
  // `force: true`: react-aria-components' usePress tracks selection as React state driven off
  // pointer events, not off the native `checked` DOM property, so Playwright's post-check
  // "did the checked property flip" verification fails even though the click itself lands. A plain
  // forced `.click()` on the real (visually-hidden-but-present) input drives the same pointer-event
  // path a real user's click would, without that verification step.
  const radioGroupNames = await page.locator('input[type="radio"]').evaluateAll((inputs) => [
    ...new Set(inputs.map((el) => el.name)),
  ]);
  for (const name of radioGroupNames) {
    const group = page.locator(`input[type="radio"][name="${name}"]`);
    if (await group.locator(':checked').count()) continue;
    await group.first().click({ force: true });
  }

  const fieldsets = await page.locator('fieldset:visible').all();
  for (const fieldset of fieldsets) {
    const checkboxes = fieldset.locator('input[type="checkbox"]');
    if (!(await checkboxes.count())) continue;
    if (await checkboxes.locator(':checked').count()) continue;
    await checkboxes.first().click({ force: true });
  }
}

// Drives "fill everything visible, click Next" until `isDone` reports true (e.g. the
// family-member banner has cleared, or the completion screen's text has appeared) - this also
// naturally absorbs conditionally-visible questions that only appear after an earlier answer in
// the same section, since each change re-fetches the template before the next fill pass runs.
export async function advanceUntil(page, isDone, { maxSteps = 40 } = {}) {
  for (let step = 0; step < maxSteps; step++) {
    if (await isDone()) return;
    await fillAllVisibleFields(page);
    await page.getByRole('button', { name: 'Next' }).click();
    await page.waitForTimeout(400);
    if (await isDone()) return;
  }
  throw new Error(`advanceUntil: condition not met within ${maxSteps} steps`);
}
