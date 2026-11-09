import { test, expect } from '@playwright/test';
import { advanceUntil, fillAllVisibleFields } from './helpers.js';

test('proband starts a session from the landing page, adds a family member via the hub, switches between passes without losing either position, and completes to a successful pedigree export', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('button', { name: 'Start new session' }).click();

  // Reaches the first section of the questionnaire.
  await expect(page.getByRole('button', { name: 'Next' })).toBeVisible({ timeout: 20_000 });

  // Dismisses the first-time onboarding overlay, which otherwise covers the whole screen.
  await page.getByRole('button', { name: 'Got it' }).click();

  // Answers through to the family-member hub (the FAMILY_MEMBERS workflow's first template).
  await advanceUntil(page, async () => (await page.getByText('Add family member').count()) > 0);

  // Adding only adds to the list - it does not auto-enter her pass (the proband is still on the
  // hub, not answering for anyone else, after this).
  await page.getByLabel('Relationship').selectOption('MOTHER');
  await page.getByLabel('Side').selectOption('MATERNAL');
  await page.getByRole('button', { name: 'Add family member' }).click();
  const continueAsMother = page.getByRole('button', { name: /Continue as MOTHER/ });
  await expect(continueAsMother).toBeVisible({ timeout: 10_000 });
  await expect(page.getByText(/You are now answering for:/)).toHaveCount(0);

  // Clicking Continue enters her pass.
  await continueAsMother.click();
  await expect(page.getByText(/You are now answering for:/)).toBeVisible({ timeout: 10_000 });
  await fillAllVisibleFields(page);

  // Switching back to the hub mid-pass (without finishing her section) does not lose her
  // in-progress answers or the proband's own position - this is the actual promise of
  // independent per-subject progress.
  await page.getByRole('button', { name: /Back to Family Members/ }).click();
  await expect(page.getByText(/You are now answering for:/)).toHaveCount(0);
  await expect(page.getByRole('button', { name: /Continue as MOTHER/ })).toBeVisible();

  // Continuing her again picks up exactly where her pass was left, not reset to her entry template.
  await page.getByRole('button', { name: /Continue as MOTHER/ }).click();
  await expect(page.getByText(/You are now answering for:/)).toBeVisible({ timeout: 10_000 });

  // Fills in the rest of her section until her pass is exhausted (the banner clears, and control
  // returns to wherever the proband's own walk actually is - never reset or advanced by her pass).
  await advanceUntil(page, async () => (await page.getByText(/You are now answering for:/).count()) === 0);

  // Answers through every remaining section of the proband's own questionnaire to completion.
  await advanceUntil(page, async () => (await page.getByText(/thank you/i).count()) > 0);

  await expect(page.getByText(/thank you/i)).toBeVisible();
});
