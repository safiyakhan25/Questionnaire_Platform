import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { vi, beforeEach } from 'vitest';
import SessionListPage from './SessionListPage';
import * as client from '../api/client';

const renderPage = () =>
  render(
    <MemoryRouter>
      <SessionListPage />
    </MemoryRouter>
  );

beforeEach(() => {
  vi.restoreAllMocks();
});

it('fetches and renders each session by its display name', async () => {
  vi.spyOn(client, 'getSessions').mockResolvedValue([
    { sessionId: 's1', displayName: 'Safiya', status: 'IN_PROGRESS', startedAt: '2026-10-30T00:00:00' },
    { sessionId: 's2', displayName: 'Unnamed - started 2026-10-29', status: 'IN_PROGRESS', startedAt: '2026-10-29T00:00:00' },
  ]);

  renderPage();

  expect(await screen.findByText('Safiya')).toBeInTheDocument();
  expect(screen.getByText('Unnamed - started 2026-10-29')).toBeInTheDocument();
});

it('clicking an in-progress session resumes it and navigates into the assessment', async () => {
  vi.spyOn(client, 'getSessions').mockResolvedValue([
    { sessionId: 's1', displayName: 'Safiya', status: 'IN_PROGRESS', startedAt: '2026-10-30T00:00:00' },
  ]);
  const startSpy = vi.spyOn(client, 'startAssessment').mockResolvedValue({ sessionId: 's1' });

  renderPage();
  fireEvent.click(await screen.findByText('Safiya'));

  await waitFor(() => expect(startSpy).toHaveBeenCalledWith({ sessionId: 's1' }));
});

it('clicking a completed session does not call startAssessment', async () => {
  vi.spyOn(client, 'getSessions').mockResolvedValue([
    { sessionId: 's1', displayName: 'Safiya', status: 'COMPLETED', startedAt: '2026-10-30T00:00:00' },
  ]);
  const startSpy = vi.spyOn(client, 'startAssessment');

  renderPage();
  fireEvent.click(await screen.findByText('Safiya'));

  await waitFor(() => expect(screen.getByText('Safiya')).toBeInTheDocument());
  expect(startSpy).not.toHaveBeenCalled();
});

it('"Start new session" calls startAssessment with no sessionId', async () => {
  vi.spyOn(client, 'getSessions').mockResolvedValue([]);
  const startSpy = vi.spyOn(client, 'startAssessment').mockResolvedValue({ sessionId: 'new-session' });

  renderPage();
  fireEvent.click(await screen.findByText('Start new session'));

  await waitFor(() => expect(startSpy).toHaveBeenCalledWith({ sessionId: null }));
});
