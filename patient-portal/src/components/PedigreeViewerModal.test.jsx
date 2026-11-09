import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { vi } from 'vitest';
import PedigreeViewerModal from './PedigreeViewerModal';
import * as client from '../api/client';

// jsdom's URL has no createObjectURL/revokeObjectURL of its own - add them as plain stubs rather
// than replacing the global URL constructor (vi.stubGlobal with a spread would strip its
// constructor behavior, which react-router and other code in this app actually relies on).
beforeEach(() => {
  URL.createObjectURL = vi.fn(() => 'blob:fake-url');
  URL.revokeObjectURL = vi.fn();
});

afterEach(() => {
  delete URL.createObjectURL;
  delete URL.revokeObjectURL;
});

function renderModal(props = {}) {
  return render(
    <MemoryRouter initialEntries={['/assessment/s1']}>
      <Routes>
        <Route path="/assessment/:sessionId" element={<PedigreeViewerModal sessionId="s1" onClose={vi.fn()} {...props} />} />
        <Route path="/completion/:sessionId" element={<div>Full pedigree page</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

it('fetches the diagram on mount and shows it as an image once loaded', async () => {
  vi.spyOn(client, 'getPedigreeDiagram').mockResolvedValue(new Blob(['<svg/>'], { type: 'image/svg+xml' }));
  renderModal();

  expect(await screen.findByRole('img')).toBeInTheDocument();
  expect(client.getPedigreeDiagram).toHaveBeenCalledWith('s1');
});

it('shows a loading state before the diagram resolves', () => {
  vi.spyOn(client, 'getPedigreeDiagram').mockReturnValue(new Promise(() => {}));
  renderModal();

  expect(screen.getByText(/loading/i)).toBeInTheDocument();
});

it('shows the error message when the service is unavailable', async () => {
  vi.spyOn(client, 'getPedigreeDiagram').mockRejectedValue(new client.ApiError(503, 'Pedigree rendering service is not reachable'));
  renderModal();

  expect(await screen.findByText('Pedigree rendering service is not reachable')).toBeInTheDocument();
});

it('calls onClose when the close button is clicked', async () => {
  vi.spyOn(client, 'getPedigreeDiagram').mockResolvedValue(new Blob(['<svg/>'], { type: 'image/svg+xml' }));
  const onClose = vi.fn();
  renderModal({ onClose });

  await screen.findByRole('img');
  fireEvent.click(screen.getByRole('button', { name: /close/i }));

  expect(onClose).toHaveBeenCalled();
});

it('revokes the object URL on unmount', async () => {
  vi.spyOn(client, 'getPedigreeDiagram').mockResolvedValue(new Blob(['<svg/>'], { type: 'image/svg+xml' }));
  const { unmount } = renderModal();

  await screen.findByRole('img');
  unmount();

  expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:fake-url');
});

it('"View in full" navigates to the same session\'s full completion/pedigree page', async () => {
  vi.spyOn(client, 'getPedigreeDiagram').mockResolvedValue(new Blob(['<svg/>'], { type: 'image/svg+xml' }));
  renderModal();

  await screen.findByRole('img');
  fireEvent.click(screen.getByRole('button', { name: /view in full/i }));

  expect(await screen.findByText('Full pedigree page')).toBeInTheDocument();
});
