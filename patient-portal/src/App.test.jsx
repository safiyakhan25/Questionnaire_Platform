import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { vi, beforeEach, afterEach } from 'vitest';
import App from './App';
import * as client from './api/client';

beforeEach(() => {
  localStorage.clear();
  localStorage.setItem('qp_onboarding_seen', 'true');
  vi.spyOn(client, 'getVisitedTemplates').mockResolvedValue([]);
});

afterEach(() => {
  delete URL.createObjectURL;
  delete URL.revokeObjectURL;
});

it('shows the session list at the root path', async () => {
  vi.spyOn(client, 'getSessions').mockResolvedValue([
    { sessionId: 's1', displayName: 'Safiya', status: 'IN_PROGRESS', startedAt: '2026-10-30T00:00:00' },
  ]);
  window.history.pushState({}, '', '/');

  render(<App />);

  expect(await screen.findByText('Safiya')).toBeInTheDocument();
  expect(screen.getByText('Start new session')).toBeInTheDocument();
});

it('an unknown path redirects to the session list, not a login screen', async () => {
  vi.spyOn(client, 'getSessions').mockResolvedValue([]);
  window.history.pushState({}, '', '/some/unknown/path');

  render(<App />);

  expect(await screen.findByText('Start new session')).toBeInTheDocument();
});

it('navigates to the completion screen once the assessment reports completed, and renders the pedigree there', async () => {
  URL.createObjectURL = vi.fn(() => 'blob:fake-url');
  URL.revokeObjectURL = vi.fn();
  window.history.pushState({}, '', '/assessment/session-1');
  vi.spyOn(client, 'getCurrentTemplate').mockResolvedValue({ templateCode: 'EXTENDED_FAMILY', completed: false, familyMemberId: null, questions: [] });
  vi.spyOn(client, 'nextTemplate').mockResolvedValue({ completed: true });
  vi.spyOn(client, 'getPedigreeDiagram').mockResolvedValue(new Blob(['<svg/>'], { type: 'image/svg+xml' }));

  render(<App />);

  fireEvent.click(await screen.findByText('Next'));

  await waitFor(() => expect(screen.getByRole('img')).toBeInTheDocument());
});
