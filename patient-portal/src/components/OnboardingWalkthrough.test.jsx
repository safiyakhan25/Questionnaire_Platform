import { render, screen, fireEvent } from '@testing-library/react';
import { vi, beforeEach } from 'vitest';
import OnboardingWalkthrough from './OnboardingWalkthrough';

beforeEach(() => {
  localStorage.clear();
});

it('shows the walkthrough steps covering answering questions, adding a family member, and going back to edit', () => {
  render(<OnboardingWalkthrough onDismiss={vi.fn()} />);

  expect(screen.getByText(/answer questions/i)).toBeInTheDocument();
  expect(screen.getByText(/add a family member/i)).toBeInTheDocument();
  expect(screen.getByText(/go back and edit/i)).toBeInTheDocument();
});

it('calls onDismiss and sets the seen flag in localStorage when dismissed', () => {
  const onDismiss = vi.fn();
  render(<OnboardingWalkthrough onDismiss={onDismiss} />);

  fireEvent.click(screen.getByText('Got it'));

  expect(onDismiss).toHaveBeenCalled();
  expect(localStorage.getItem('qp_onboarding_seen')).toBe('true');
});
