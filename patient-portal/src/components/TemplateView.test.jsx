import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { vi, beforeEach } from 'vitest';
import TemplateView from './TemplateView';
import * as client from '../api/client';

beforeEach(() => {
  vi.spyOn(client, 'getVisitedTemplates').mockResolvedValue([]);
});

// PedigreeViewerModal (mounted conditionally inside TemplateView) calls useNavigate, which throws
// outside a Router context - every render/rerender in this file needs one, even tests that never
// open the modal, since the component tree is defined once up front.
function renderTV(props) {
  return render(
    <MemoryRouter>
      <TemplateView {...props} />
    </MemoryRouter>,
  );
}

function rerenderTV(rerender, props) {
  return rerender(
    <MemoryRouter>
      <TemplateView {...props} />
    </MemoryRouter>,
  );
}

it('carries the app brand heading in the sidebar, linking back to the session list', async () => {
  vi.spyOn(client, 'getCurrentTemplate').mockResolvedValue({ templateCode: 'DEMOGRAPHICS', completed: false, familyMemberId: null, questions: [] });

  renderTV({ sessionId: 's1', familyMemberId: null, subjectLabel: 'you', onCompleted: vi.fn(), onFamilyMemberPassEnded: vi.fn() });

  expect(await screen.findByRole('link', { name: 'QuestionnairePlatform' })).toHaveAttribute('href', '/');
});

it('renders the questions from the fetched template', async () => {
  vi.spyOn(client, 'getCurrentTemplate').mockResolvedValue({
    templateCode: 'DEMOGRAPHICS', completed: false, familyMemberId: null,
    questions: [{ id: 'q1', answerKey: 'first_name', text: 'First name', type: 'TEXT', options: null, validation: null, visible: true, value: null }],
  });

  renderTV({ sessionId: 's1', familyMemberId: null, subjectLabel: 'you', onCompleted: vi.fn(), onFamilyMemberPassEnded: vi.fn() });

  await waitFor(() => expect(screen.getByLabelText(/^First name/)).toBeInTheDocument());
});

it('submits an answer and re-fetches the current template on a field change', async () => {
  vi.spyOn(client, 'getCurrentTemplate').mockResolvedValue({
    templateCode: 'DEMOGRAPHICS', completed: false, familyMemberId: null,
    questions: [{ id: 'q1', answerKey: 'first_name', text: 'First name', type: 'TEXT', options: null, validation: null, visible: true, value: null }],
  });
  const submitSpy = vi.spyOn(client, 'submitAnswer').mockResolvedValue({});

  renderTV({ sessionId: 's1', familyMemberId: null, subjectLabel: 'you', onCompleted: vi.fn(), onFamilyMemberPassEnded: vi.fn() });
  await screen.findByLabelText(/^First name/);

  fireEvent.change(screen.getByLabelText(/^First name/), { target: { value: 'Sarah' } });

  await waitFor(() => expect(submitSpy).toHaveBeenCalledWith('s1', { questionId: 'q1', value: 'Sarah', familyMemberId: null }));
  expect(client.getCurrentTemplate).toHaveBeenCalledTimes(2);
});

it('shows a "you are now answering for" banner only when familyMemberId is set', async () => {
  vi.spyOn(client, 'getCurrentTemplate').mockResolvedValue({ templateCode: 'DEMOGRAPHICS', completed: false, familyMemberId: 'fm1', questions: [] });

  const { rerender } = renderTV({ sessionId: 's1', familyMemberId: 'fm1', subjectLabel: 'Mother', onCompleted: vi.fn(), onFamilyMemberPassEnded: vi.fn() });
  await waitFor(() => expect(screen.getByText('You are now answering for: Mother')).toBeInTheDocument());

  rerenderTV(rerender, { sessionId: 's1', familyMemberId: null, subjectLabel: 'you', onCompleted: vi.fn(), onFamilyMemberPassEnded: vi.fn() });
  await waitFor(() => expect(screen.queryByText(/You are now answering for/)).not.toBeInTheDocument());
});

it("shows the backend's 409 message instead of navigating when required fields are missing", async () => {
  vi.spyOn(client, 'getCurrentTemplate').mockResolvedValue({ templateCode: 'DEMOGRAPHICS', completed: false, familyMemberId: null, questions: [] });
  const err = Object.assign(new Error('required question(s) unanswered: first_name'), { status: 409 });
  vi.spyOn(client, 'nextTemplate').mockRejectedValue(err);
  const onCompleted = vi.fn();

  renderTV({ sessionId: 's1', familyMemberId: null, subjectLabel: 'you', onCompleted, onFamilyMemberPassEnded: vi.fn() });
  fireEvent.click(await screen.findByText('Next'));

  await waitFor(() => expect(screen.getByText(/first_name/)).toBeInTheDocument());
  expect(onCompleted).not.toHaveBeenCalled();
});

it('calls onCompleted when next-template reports completed:true', async () => {
  vi.spyOn(client, 'getCurrentTemplate').mockResolvedValue({ templateCode: 'EXTENDED_FAMILY', completed: false, familyMemberId: null, questions: [] });
  vi.spyOn(client, 'nextTemplate').mockResolvedValue({ completed: true });
  const onCompleted = vi.fn();

  renderTV({ sessionId: 's1', familyMemberId: null, subjectLabel: 'you', onCompleted, onFamilyMemberPassEnded: vi.fn() });
  fireEvent.click(await screen.findByText('Next'));

  await waitFor(() => expect(onCompleted).toHaveBeenCalled());
});

it('resuming a visited template replaces the displayed questions with that template\'s own', async () => {
  vi.spyOn(client, 'getCurrentTemplate').mockResolvedValue({
    templateCode: 'PARENTS', completed: false, familyMemberId: null,
    questions: [{ id: 'q-parents', answerKey: 'father_name', text: "Father's name", type: 'TEXT', options: null, validation: null, visible: true, value: null }],
  });
  vi.spyOn(client, 'getVisitedTemplates').mockResolvedValue([
    { templateId: 't1', templateCode: 'DEMOGRAPHICS', templateName: 'Demographics' },
  ]);
  vi.spyOn(client, 'resumeAtTemplate').mockResolvedValue({
    templateCode: 'DEMOGRAPHICS', completed: false, familyMemberId: null,
    questions: [{ id: 'q-demo', answerKey: 'first_name', text: 'First name', type: 'TEXT', options: null, validation: null, visible: true, value: 'Sarah' }],
  });

  renderTV({ sessionId: 's1', familyMemberId: null, subjectLabel: 'you', onCompleted: vi.fn(), onFamilyMemberPassEnded: vi.fn() });
  await screen.findByLabelText(/^Father's name/);

  fireEvent.click(await screen.findByText('Demographics'));

  await waitFor(() => expect(screen.getByLabelText(/^First name/)).toBeInTheDocument());
  expect(screen.queryByLabelText(/^Father's name/)).not.toBeInTheDocument();
});

it('calls onFamilyMemberPassEnded (not onCompleted) when a family member\'s own pass is exhausted', async () => {
  vi.spyOn(client, 'getCurrentTemplate').mockResolvedValue({ templateCode: 'FAMILY_INFO', completed: false, familyMemberId: 'fm1', questions: [] });
  vi.spyOn(client, 'nextTemplate').mockResolvedValue({ completed: true, familyMemberId: 'fm1' });
  const onFamilyMemberPassEnded = vi.fn();
  const onCompleted = vi.fn();

  renderTV({ sessionId: 's1', familyMemberId: 'fm1', subjectLabel: 'Mother', onCompleted, onFamilyMemberPassEnded });
  fireEvent.click(await screen.findByText('Next'));

  await waitFor(() => expect(onFamilyMemberPassEnded).toHaveBeenCalled());
  expect(onCompleted).not.toHaveBeenCalled();
});

it('threads familyMemberId through getCurrentTemplate and nextTemplate', async () => {
  vi.spyOn(client, 'getCurrentTemplate').mockResolvedValue({ templateCode: 'FAMILY_INFO', completed: false, familyMemberId: 'fm1', questions: [] });
  vi.spyOn(client, 'nextTemplate').mockResolvedValue({ completed: false, familyMemberId: 'fm1', templateCode: 'FAMILY_INFO', questions: [] });

  renderTV({ sessionId: 's1', familyMemberId: 'fm1', subjectLabel: 'Mother', onCompleted: vi.fn(), onFamilyMemberPassEnded: vi.fn() });
  await waitFor(() => expect(client.getCurrentTemplate).toHaveBeenCalledWith('s1', 'fm1'));

  fireEvent.click(await screen.findByText('Next'));
  await waitFor(() => expect(client.nextTemplate).toHaveBeenCalledWith('s1', 'fm1'));
});

it('renders the family-member hub instead of questions when the template is FAMILY_MEMBER_HUB', async () => {
  vi.spyOn(client, 'getCurrentTemplate').mockResolvedValue({ templateCode: 'FAMILY_MEMBER_HUB', completed: false, familyMemberId: null, questions: [] });
  vi.spyOn(client, 'getFamilyMembers').mockResolvedValue([]);

  renderTV({ sessionId: 's1', familyMemberId: null, subjectLabel: 'you', onCompleted: vi.fn(), onFamilyMemberPassEnded: vi.fn(), onContinueFamilyMember: vi.fn() });

  expect(await screen.findByText('Add family member')).toBeInTheDocument();
});

it('clicking Continue in the hub calls onContinueFamilyMember with the chosen family member', async () => {
  vi.spyOn(client, 'getCurrentTemplate').mockResolvedValue({ templateCode: 'FAMILY_MEMBER_HUB', completed: false, familyMemberId: null, questions: [] });
  vi.spyOn(client, 'getFamilyMembers').mockResolvedValue([
    { id: 'fm1', roles: [{ scopeId: 'p1', relationshipType: 'FATHER' }] },
  ]);
  const onContinueFamilyMember = vi.fn();

  renderTV({ sessionId: 's1', familyMemberId: null, subjectLabel: 'you', onCompleted: vi.fn(), onFamilyMemberPassEnded: vi.fn(), onContinueFamilyMember });
  fireEvent.click(await screen.findByRole('button', { name: /Continue.*FATHER/i }));

  expect(onContinueFamilyMember).toHaveBeenCalledWith('fm1', 'FATHER');
});

it('clicking "Back to Family Members" calls onBackToFamilyMembers', async () => {
  vi.spyOn(client, 'getCurrentTemplate').mockResolvedValue({ templateCode: 'FAMILY_INFO', completed: false, familyMemberId: 'fm1', questions: [] });
  const onBackToFamilyMembers = vi.fn();

  renderTV({ sessionId: 's1', familyMemberId: 'fm1', subjectLabel: 'Mother', onCompleted: vi.fn(), onFamilyMemberPassEnded: vi.fn(), onBackToFamilyMembers });
  fireEvent.click(await screen.findByText(/Back to Family Members/));

  expect(onBackToFamilyMembers).toHaveBeenCalled();
});

it('shows a Pedigree Viewer button that opens the modal, and closing it removes the modal', async () => {
  vi.spyOn(client, 'getCurrentTemplate').mockResolvedValue({ templateCode: 'DEMOGRAPHICS', completed: false, familyMemberId: null, questions: [] });
  vi.spyOn(client, 'getPedigreeDiagram').mockReturnValue(new Promise(() => {}));

  renderTV({ sessionId: 's1', familyMemberId: null, subjectLabel: 'you', onCompleted: vi.fn(), onFamilyMemberPassEnded: vi.fn() });

  fireEvent.click(await screen.findByRole('button', { name: /pedigree viewer/i }));
  expect(screen.getByRole('dialog')).toBeInTheDocument();

  fireEvent.click(screen.getByRole('button', { name: /close/i }));
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
});

it('shows a browse message and the visited-templates nav instead of a blank panel when the session is already completed', async () => {
  vi.spyOn(client, 'getCurrentTemplate').mockResolvedValue({ templateCode: null, templateId: null, completed: true, familyMemberId: null, questions: [] });
  vi.spyOn(client, 'getVisitedTemplates').mockResolvedValue([{ templateId: 't1', templateCode: 'DEMOGRAPHICS', templateName: 'Demographics' }]);

  renderTV({ sessionId: 's1', familyMemberId: null, subjectLabel: 'you', onCompleted: vi.fn(), onFamilyMemberPassEnded: vi.fn() });

  expect(await screen.findByText(/this assessment is complete/i)).toBeInTheDocument();
  // VisitedTemplatesNav fetches its own list independently of the main template fetch above -
  // awaiting it separately rather than assuming it resolves in the same tick.
  expect(await screen.findByText('Demographics')).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: 'Next' })).not.toBeInTheDocument();
});

it('does not show a Previous button on the first template of a subject\'s walk', async () => {
  vi.spyOn(client, 'getCurrentTemplate').mockResolvedValue({ templateId: 't1', templateCode: 'DEMOGRAPHICS', completed: false, familyMemberId: null, questions: [] });

  renderTV({ sessionId: 's1', familyMemberId: null, subjectLabel: 'you', onCompleted: vi.fn(), onFamilyMemberPassEnded: vi.fn() });
  await screen.findByText('Next');

  expect(screen.queryByRole('button', { name: 'Previous' })).not.toBeInTheDocument();
});

it('Previous appears after advancing, and navigates back to the template it came from', async () => {
  vi.spyOn(client, 'getCurrentTemplate').mockResolvedValue({ templateId: 't1', templateCode: 'DEMOGRAPHICS', completed: false, familyMemberId: null, questions: [] });
  vi.spyOn(client, 'nextTemplate').mockResolvedValue({ templateId: 't2', templateCode: 'PARENTS', completed: false, familyMemberId: null, questions: [] });
  const resumeSpy = vi.spyOn(client, 'resumeAtTemplate').mockResolvedValue({
    templateId: 't1', templateCode: 'DEMOGRAPHICS', completed: false, familyMemberId: null, questions: [],
  });

  renderTV({ sessionId: 's1', familyMemberId: null, subjectLabel: 'you', onCompleted: vi.fn(), onFamilyMemberPassEnded: vi.fn() });
  await screen.findByText('DEMOGRAPHICS');
  expect(screen.queryByRole('button', { name: 'Previous' })).not.toBeInTheDocument();

  fireEvent.click(screen.getByRole('button', { name: 'Next' }));
  await screen.findByText('PARENTS');

  fireEvent.click(await screen.findByRole('button', { name: 'Previous' }));

  await waitFor(() => expect(resumeSpy).toHaveBeenCalledWith('s1', 't1', null));
  await waitFor(() => expect(screen.queryByRole('button', { name: 'Previous' })).not.toBeInTheDocument());
  expect(screen.getByText('DEMOGRAPHICS')).toBeInTheDocument();
});

it('jumping via the sidebar also makes Previous available, undoing back to the template left behind', async () => {
  vi.spyOn(client, 'getCurrentTemplate').mockResolvedValue({
    templateId: 't2', templateCode: 'PARENTS', completed: false, familyMemberId: null, questions: [],
  });
  vi.spyOn(client, 'getVisitedTemplates').mockResolvedValue([
    { templateId: 't1', templateCode: 'DEMOGRAPHICS', templateName: 'Demographics' },
  ]);
  const resumeSpy = vi.spyOn(client, 'resumeAtTemplate')
    .mockResolvedValueOnce({ templateId: 't1', templateCode: 'DEMOGRAPHICS', completed: false, familyMemberId: null, questions: [] })
    .mockResolvedValueOnce({ templateId: 't2', templateCode: 'PARENTS', completed: false, familyMemberId: null, questions: [] });

  renderTV({ sessionId: 's1', familyMemberId: null, subjectLabel: 'you', onCompleted: vi.fn(), onFamilyMemberPassEnded: vi.fn() });
  await screen.findByText('PARENTS');

  fireEvent.click(await screen.findByText('Demographics'));
  await screen.findByText('DEMOGRAPHICS');

  fireEvent.click(screen.getByRole('button', { name: 'Previous' }));

  await waitFor(() => expect(resumeSpy).toHaveBeenCalledWith('s1', 't2', null));
});
