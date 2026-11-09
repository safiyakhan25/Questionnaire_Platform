import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import { vi } from 'vitest';
import FamilyMemberHub from './FamilyMemberHub';
import * as client from '../api/client';

it('fetches and renders previously added family members, each with a Continue button', async () => {
  vi.spyOn(client, 'getFamilyMembers').mockResolvedValue([
    { id: 'fm1', roles: [{ scopeId: 'p1', relationshipType: 'FATHER' }] },
  ]);

  render(<FamilyMemberHub sessionId="s1" onContinue={vi.fn()} />);

  expect(await screen.findByRole('button', { name: /Continue.*FATHER/i })).toBeInTheDocument();
});

it('submitting the add form only adds to the list - it does not call onContinue', async () => {
  vi.spyOn(client, 'getFamilyMembers').mockResolvedValue([]);
  const created = { id: 'fm1', roles: [{ scopeId: 'p1', relationshipType: 'MOTHER' }] };
  const addSpy = vi.spyOn(client, 'addFamilyMember').mockResolvedValue(created);
  const onContinue = vi.fn();

  render(<FamilyMemberHub sessionId="s1" onContinue={onContinue} />);

  fireEvent.change(await screen.findByLabelText(/^Relationship/), { target: { value: 'MOTHER' } });
  fireEvent.change(screen.getByLabelText(/^Side/), { target: { value: 'MATERNAL' } });
  fireEvent.click(screen.getByText('Add family member'));

  await waitFor(() => expect(addSpy).toHaveBeenCalledWith('s1', { relationshipType: 'MOTHER', maternalPaternalSide: 'MATERNAL', scopeId: null }));
  expect(onContinue).not.toHaveBeenCalled();
});

it('offers a "related to" picker defaulting to the proband, listing already-added family members, and sends the chosen scopeId', async () => {
  vi.spyOn(client, 'getFamilyMembers').mockResolvedValue([
    { id: 'fm1', roles: [{ scopeId: 'p1', relationshipType: 'MOTHER' }] },
  ]);
  const addSpy = vi.spyOn(client, 'addFamilyMember').mockResolvedValue({ id: 'fm2', roles: [] });

  render(<FamilyMemberHub sessionId="s1" onContinue={vi.fn()} />);
  await screen.findByRole('button', { name: /Continue.*MOTHER/i });

  const relatedToSelect = screen.getByLabelText(/^Related to/);
  expect(within(relatedToSelect).getByRole('option', { name: 'Proband' })).toBeInTheDocument();
  expect(within(relatedToSelect).getByRole('option', { name: 'MOTHER' })).toBeInTheDocument();

  fireEvent.change(screen.getByLabelText(/^Relationship/), { target: { value: 'GRANDFATHER' } });
  fireEvent.change(relatedToSelect, { target: { value: 'fm1' } });
  fireEvent.click(screen.getByText('Add family member'));

  await waitFor(() => expect(addSpy).toHaveBeenCalledWith('s1', { relationshipType: 'GRANDFATHER', maternalPaternalSide: 'MATERNAL', scopeId: 'fm1' }));
});

it('clicking Continue on a row calls onContinue with that family member\'s id and label', async () => {
  vi.spyOn(client, 'getFamilyMembers').mockResolvedValue([
    { id: 'fm1', roles: [{ scopeId: 'p1', relationshipType: 'FATHER' }] },
  ]);
  const onContinue = vi.fn();

  render(<FamilyMemberHub sessionId="s1" onContinue={onContinue} />);
  fireEvent.click(await screen.findByRole('button', { name: /Continue.*FATHER/i }));

  expect(onContinue).toHaveBeenCalledWith('fm1', 'FATHER');
});

it('renders prevSlot and nextSlot next to the add-family-member submit button, when provided', async () => {
  vi.spyOn(client, 'getFamilyMembers').mockResolvedValue([]);

  render(
    <FamilyMemberHub
      sessionId="s1"
      onContinue={vi.fn()}
      prevSlot={<button type="button">Previous</button>}
      nextSlot={<button type="button">Next</button>}
    />,
  );

  expect(await screen.findByRole('button', { name: 'Previous' })).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Next' })).toBeInTheDocument();
});
