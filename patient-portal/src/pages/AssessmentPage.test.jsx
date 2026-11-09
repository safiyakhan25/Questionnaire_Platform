import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { vi, beforeEach } from 'vitest';
import AssessmentPage from './AssessmentPage';
import * as client from '../api/client';

beforeEach(() => {
  vi.spyOn(client, 'getVisitedTemplates').mockResolvedValue([]);
  localStorage.clear();
  localStorage.setItem('qp_onboarding_seen', 'true');
});

// TemplateView's sidebar now carries its own brand Link (since the app-wide navbar was removed),
// so every render here needs a Router context, not just the ones that open the pedigree modal.
function renderPage(props) {
  return render(
    <MemoryRouter>
      <AssessmentPage {...props} />
    </MemoryRouter>,
  );
}

it('renders the current template for the given sessionId, with no bootstrap step', async () => {
  vi.spyOn(client, 'getCurrentTemplate').mockResolvedValue({
    templateCode: 'DEMOGRAPHICS', completed: false, familyMemberId: null,
    questions: [{ id: 'q1', answerKey: 'first_name', text: 'First name', type: 'TEXT', options: null, validation: null, visible: true, value: null }],
  });

  renderPage({ sessionId: 'existing-session-id' });

  await waitFor(() => expect(screen.getByLabelText(/^First name/)).toBeInTheDocument());
  expect(client.getCurrentTemplate).toHaveBeenCalledWith('existing-session-id', null);
});

it('shows the onboarding walkthrough on a first-ever visit and dismissing it hides it for good', async () => {
  localStorage.removeItem('qp_onboarding_seen');
  vi.spyOn(client, 'getCurrentTemplate').mockResolvedValue({ templateCode: 'DEMOGRAPHICS', completed: false, familyMemberId: null, questions: [] });

  renderPage({ sessionId: 'existing-session-id' });

  fireEvent.click(await screen.findByText('Got it'));

  expect(screen.queryByText('Got it')).not.toBeInTheDocument();
  expect(localStorage.getItem('qp_onboarding_seen')).toBe('true');
});

it('does not show the onboarding walkthrough on a later visit', async () => {
  vi.spyOn(client, 'getCurrentTemplate').mockResolvedValue({ templateCode: 'DEMOGRAPHICS', completed: false, familyMemberId: null, questions: [] });

  renderPage({ sessionId: 'existing-session-id' });
  await screen.findByText('Next');

  expect(screen.queryByText('Got it')).not.toBeInTheDocument();
});
