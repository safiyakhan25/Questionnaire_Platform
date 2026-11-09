import { render, screen, fireEvent } from '@testing-library/react';
import { vi } from 'vitest';
import GroupListField from './GroupListField';

function stubRenderField(fieldDef, rowValue, onRowFieldChange) {
  return (
    <input
      aria-label={fieldDef.label}
      value={rowValue ?? ''}
      onChange={(e) => onRowFieldChange(fieldDef.name, e.target.value)}
    />
  );
}

it('starts with zero rows and adding a row appends an empty object', () => {
  const onChange = vi.fn();
  render(<GroupListField fields={[{ name: 'role', label: 'Role' }]} value={[]} onChange={onChange} renderField={stubRenderField} />);
  fireEvent.click(screen.getByText(/add/i));
  expect(onChange).toHaveBeenCalledWith([{}]);
});

it('editing a field in a row updates only that row', () => {
  const onChange = vi.fn();
  render(<GroupListField fields={[{ name: 'role', label: 'Role' }]} value={[{ role: 'A' }, { role: 'B' }]} onChange={onChange} renderField={stubRenderField} />);
  fireEvent.change(screen.getAllByLabelText('Role')[1], { target: { value: 'C' } });
  expect(onChange).toHaveBeenCalledWith([{ role: 'A' }, { role: 'C' }]);
});

it('removing a row drops it from the array', () => {
  const onChange = vi.fn();
  render(<GroupListField fields={[{ name: 'role', label: 'Role' }]} value={[{ role: 'A' }, { role: 'B' }]} onChange={onChange} renderField={stubRenderField} />);
  fireEvent.click(screen.getAllByText(/remove/i)[0]);
  expect(onChange).toHaveBeenCalledWith([{ role: 'B' }]);
});

it('does not propagate a row that is missing a required field', () => {
  const onChange = vi.fn();
  render(<GroupListField fields={[{ name: 'role', label: 'Role', required: true }]} value={[]} onChange={onChange} renderField={stubRenderField} />);
  fireEvent.click(screen.getByText(/add/i));
  expect(onChange).toHaveBeenCalledWith([]);
});

it('propagates a row once its required field is filled in', () => {
  const onChange = vi.fn();
  render(<GroupListField fields={[{ name: 'role', label: 'Role', required: true }]} value={[]} onChange={onChange} renderField={stubRenderField} />);
  fireEvent.click(screen.getByText(/add/i));
  fireEvent.change(screen.getByLabelText('Role'), { target: { value: 'EGG_DONOR' } });
  expect(onChange).toHaveBeenLastCalledWith([{ role: 'EGG_DONOR' }]);
});

it('clearing a field to an empty string stores null, not an empty string', () => {
  const onChange = vi.fn();
  render(<GroupListField fields={[{ name: 'notes', label: 'Notes' }]} value={[{ notes: 'x' }]} onChange={onChange} renderField={stubRenderField} />);
  fireEvent.change(screen.getByLabelText('Notes'), { target: { value: '' } });
  expect(onChange).toHaveBeenLastCalledWith([{ notes: null }]);
});

it('a complete row stops being propagated once a required field is cleared back out', () => {
  const onChange = vi.fn();
  render(<GroupListField fields={[{ name: 'role', label: 'Role', required: true }]} value={[{ role: 'EGG_DONOR' }]} onChange={onChange} renderField={stubRenderField} />);
  fireEvent.change(screen.getByLabelText('Role'), { target: { value: '' } });
  expect(onChange).toHaveBeenLastCalledWith([]);
});
