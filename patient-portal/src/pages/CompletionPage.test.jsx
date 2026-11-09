import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { vi } from 'vitest';
import CompletionPage from './CompletionPage';
import * as client from '../api/client';

// See PedigreeViewerModal.test.jsx for why these are added directly rather than via
// vi.stubGlobal (which would strip URL's constructor behavior - react-router needs it intact).
beforeEach(() => {
  URL.createObjectURL = vi.fn(() => 'blob:fake-url');
  URL.revokeObjectURL = vi.fn();
});

afterEach(() => {
  delete URL.createObjectURL;
  delete URL.revokeObjectURL;
});

function renderPage(sessionId = 's1') {
  return render(
    <MemoryRouter>
      <CompletionPage sessionId={sessionId} />
    </MemoryRouter>,
  );
}

it('shows the rendered diagram once it loads', async () => {
  vi.spyOn(client, 'getPedigreeDiagram').mockResolvedValue(new Blob(['<svg/>'], { type: 'image/svg+xml' }));
  renderPage();

  expect(await screen.findByRole('img')).toBeInTheDocument();
});

it('shows an error message when the diagram cannot be rendered', async () => {
  vi.spyOn(client, 'getPedigreeDiagram').mockRejectedValue(new client.ApiError(503, 'Pedigree rendering service is not reachable'));
  renderPage();

  expect(await screen.findByText('Pedigree rendering service is not reachable')).toBeInTheDocument();
});

it('has an Edit answers link back into the assessment', async () => {
  vi.spyOn(client, 'getPedigreeDiagram').mockResolvedValue(new Blob(['<svg/>'], { type: 'image/svg+xml' }));
  renderPage('s1');

  await screen.findByRole('img');
  expect(screen.getByRole('link', { name: /edit answers/i })).toHaveAttribute('href', '/assessment/s1');
});

it('offers SVG, PNG, and PDF downloads', async () => {
  vi.spyOn(client, 'getPedigreeDiagram').mockResolvedValue(new Blob(['<svg/>'], { type: 'image/svg+xml' }));
  renderPage();

  await screen.findByRole('img');
  expect(screen.getByRole('button', { name: /svg/i })).toBeInTheDocument();
  expect(screen.getByRole('button', { name: /png/i })).toBeInTheDocument();
  expect(screen.getByRole('button', { name: /pdf/i })).toBeInTheDocument();
});

it('clicking a download button fetches that format and triggers a download', async () => {
  const spy = vi.spyOn(client, 'getPedigreeDiagram').mockResolvedValue(new Blob(['<svg/>'], { type: 'image/svg+xml' }));
  renderPage('s1');
  await screen.findByRole('img');

  fireEvent.click(screen.getByRole('button', { name: /png/i }));

  await waitFor(() => expect(spy).toHaveBeenCalledWith('s1', 'png'));
});

it('revokes the diagram object URL on unmount', async () => {
  vi.spyOn(client, 'getPedigreeDiagram').mockResolvedValue(new Blob(['<svg/>'], { type: 'image/svg+xml' }));
  const { unmount } = renderPage();

  await screen.findByRole('img');
  unmount();

  expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:fake-url');
});

it('shows an error message when a download fails instead of failing silently', async () => {
  vi.spyOn(client, 'getPedigreeDiagram')
    .mockResolvedValueOnce(new Blob(['<svg/>'], { type: 'image/svg+xml' }))
    .mockRejectedValueOnce(new client.ApiError(503, 'Pedigree rendering service is not reachable'));
  renderPage('s1');
  await screen.findByRole('img');

  fireEvent.click(screen.getByRole('button', { name: /pdf/i }));

  expect(await screen.findByText('Pedigree rendering service is not reachable')).toBeInTheDocument();
});
