import { render, screen, fireEvent } from '@testing-library/react';
import { vi } from 'vitest';
import FamilyMemberSelect from './FamilyMemberSelect';
import * as client from '../api/client';

it('single-select mode lists family members except the excluded id and reports the chosen id', async () => {
  vi.spyOn(client, 'getFamilyMembers').mockResolvedValue([
    { id: 'fm1', roles: [{ relationshipType: 'MOTHER' }] },
    { id: 'fm2', roles: [{ relationshipType: 'SISTER' }] },
  ]);
  const onChange = vi.fn();
  render(<FamilyMemberSelect sessionId="s1" excludeId="fm2" multiple={false} value={null} onChange={onChange} />);
  const select = await screen.findByRole('combobox');
  expect(screen.queryByText('SISTER')).not.toBeInTheDocument();
  fireEvent.change(select, { target: { value: 'fm1' } });
  expect(onChange).toHaveBeenCalledWith('fm1');
});

it('multi-select mode reports an array of chosen ids', async () => {
  vi.spyOn(client, 'getFamilyMembers').mockResolvedValue([{ id: 'fm1', roles: [{ relationshipType: 'MOTHER' }] }]);
  const onChange = vi.fn();
  render(<FamilyMemberSelect sessionId="s1" excludeId={null} multiple value={[]} onChange={onChange} />);
  fireEvent.click(await screen.findByLabelText('MOTHER'));
  expect(onChange).toHaveBeenCalledWith(['fm1']);
});

it('single-select mode exposes the field label as the select\'s accessible name', async () => {
  vi.spyOn(client, 'getFamilyMembers').mockResolvedValue([{ id: 'fm1', roles: [{ relationshipType: 'MOTHER' }] }]);
  render(<FamilyMemberSelect sessionId="s1" excludeId={null} multiple={false} value={null} label="Related to" onChange={vi.fn()} />);
  await screen.findByRole('combobox');
  expect(screen.getByLabelText(/^Related to/)).toBeInTheDocument();
});

it('multi-select mode groups its checkboxes under the field label', async () => {
  vi.spyOn(client, 'getFamilyMembers').mockResolvedValue([{ id: 'fm1', roles: [{ relationshipType: 'MOTHER' }] }]);
  render(<FamilyMemberSelect sessionId="s1" excludeId={null} multiple value={[]} label="Twins with" onChange={vi.fn()} />);
  expect(await screen.findByRole('group', { name: /^Twins with/ })).toBeInTheDocument();
});
