import { render, screen, fireEvent } from '@testing-library/react';
import { vi } from 'vitest';
import FieldRenderer from './FieldRenderer';
import * as client from '../api/client';

it('TEXT: delegates to TextField and reports string changes', () => {
  const field = { name: 'diseaseType', label: 'Disease name', type: 'TEXT', required: true };
  const onChange = vi.fn();
  render(<FieldRenderer field={field} value={null} onChange={onChange} sessionId="s1" excludeId={null} />);
  fireEvent.change(screen.getByLabelText(/^Disease name/), { target: { value: 'Breast Cancer' } });
  expect(onChange).toHaveBeenCalledWith('Breast Cancer');
});

it('DROPDOWN: delegates to DropdownField with the field\'s own options', () => {
  const field = { name: 'twinType', label: 'Type', type: 'DROPDOWN', required: true,
    options: [{ label: 'Identical', value: 'MONOZYGOTIC' }] };
  const onChange = vi.fn();
  render(<FieldRenderer field={field} value={null} onChange={onChange} sessionId="s1" excludeId={null} />);
  fireEvent.change(screen.getByLabelText(/^Type/), { target: { value: 'MONOZYGOTIC' } });
  expect(onChange).toHaveBeenCalledWith('MONOZYGOTIC');
});

it('FAMILY_MEMBER_SELECT: delegates to FamilyMemberSelect in single mode and shows the field\'s own label', async () => {
  vi.spyOn(client, 'getFamilyMembers').mockResolvedValue([{ id: 'fm1', roles: [{ relationshipType: 'MOTHER' }] }]);
  const field = { name: 'familyMemberId', label: 'Family member', type: 'FAMILY_MEMBER_SELECT', required: true };
  const onChange = vi.fn();
  render(<FieldRenderer field={field} value={null} onChange={onChange} sessionId="s1" excludeId={null} />);
  expect(screen.getByText('Family member')).toBeInTheDocument();
  const select = await screen.findByRole('combobox');
  fireEvent.change(select, { target: { value: 'fm1' } });
  expect(onChange).toHaveBeenCalledWith('fm1');
});

it('FAMILY_MEMBER_MULTI_SELECT: delegates to FamilyMemberSelect in multi mode', async () => {
  vi.spyOn(client, 'getFamilyMembers').mockResolvedValue([{ id: 'fm1', roles: [{ relationshipType: 'MOTHER' }] }]);
  const field = { name: 'coTwinFamilyMemberIds', label: 'Co-twins', type: 'FAMILY_MEMBER_MULTI_SELECT', required: true };
  const onChange = vi.fn();
  render(<FieldRenderer field={field} value={[]} onChange={onChange} sessionId="s1" excludeId={null} />);
  fireEvent.click(await screen.findByLabelText('MOTHER'));
  expect(onChange).toHaveBeenCalledWith(['fm1']);
});

it('GROUP_LIST: shows the field\'s own label and, once a row is complete, produces the nested array', () => {
  const field = { name: 'participants', label: 'Participants', type: 'GROUP_LIST', fields: [
    { name: 'role', label: 'Role', type: 'TEXT', required: true },
  ] };
  const onChange = vi.fn();
  render(<FieldRenderer field={field} value={[]} onChange={onChange} sessionId="s1" excludeId={null} />);
  expect(screen.getByText('Participants')).toBeInTheDocument();
  fireEvent.click(screen.getByText(/add/i));
  fireEvent.change(screen.getByLabelText(/^Role/), { target: { value: 'EGG_DONOR' } });
  expect(onChange).toHaveBeenCalledWith([{ role: 'EGG_DONOR' }]);
});
