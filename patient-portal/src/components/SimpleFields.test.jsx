import { render, screen, fireEvent } from '@testing-library/react';
import { vi } from 'vitest';
import { TextField, NumberField, DateField, DropdownField } from './SimpleFields';

// Untitled UI's shared Label component renders a required-indicator "*" span whose visibility is
// CSS-only (hidden unless the field is actually required) - jsdom never applies that stylesheet,
// so the span's text is always present and the computed label text is always "<label>*". Matching
// with an anchored-prefix regex instead of an exact string tolerates that consistently.

it('TextField reports string changes', () => {
  const onChange = vi.fn();
  render(<TextField fieldId="q" label="First name" value={null} required={false} validation={null} onChange={onChange} />);
  fireEvent.change(screen.getByLabelText(/^First name/), { target: { value: 'Sarah' } });
  expect(onChange).toHaveBeenCalledWith('Sarah');
});

it('NumberField reports numeric changes', () => {
  const onChange = vi.fn();
  render(<NumberField fieldId="q" label="Age" value={null} required={false} validation={null} onChange={onChange} />);
  fireEvent.change(screen.getByLabelText(/^Age/), { target: { value: '42' } });
  expect(onChange).toHaveBeenCalledWith(42);
});

it('DateField reports date changes', () => {
  const onChange = vi.fn();
  render(<DateField fieldId="q" label="Date of birth" value={null} required={false} onChange={onChange} />);
  fireEvent.change(screen.getByLabelText(/^Date of birth/), { target: { value: '2024-01-15' } });
  expect(onChange).toHaveBeenCalledWith('2024-01-15');
});

it('DropdownField reports the selected raw value from the options array', () => {
  const onChange = vi.fn();
  render(<DropdownField fieldId="q" label="Blood type" value={null} required={false}
    options={[{ label: 'A+', value: 'A_POS' }, { label: 'O-', value: 'O_NEG' }]} onChange={onChange} />);
  fireEvent.change(screen.getByLabelText(/^Blood type/), { target: { value: 'O_NEG' } });
  expect(onChange).toHaveBeenCalledWith('O_NEG');
});

// NativeSelect's own vendored Label call never passes isRequired through, so its "*" is gated on a
// `group-required:` ancestor that NativeSelect's wrapper doesn't provide - DropdownField forces it
// visible with its own className instead of editing the vendored file. jsdom never applies CSS, so
// this only pins that the forcing class is present, not that it actually renders visibly.
it('DropdownField forces the required asterisk visible when required, since NativeSelect cannot show it on its own', () => {
  const { container } = render(<DropdownField fieldId="q" label="Blood type" value={null} required
    options={[{ label: 'A+', value: 'A_POS' }]} onChange={vi.fn()} />);
  expect(container.querySelector('.mb-4 > div').className).toContain('[&_[data-label]>span]:block');
});

it('DropdownField does not add the forcing class when not required', () => {
  const { container } = render(<DropdownField fieldId="q" label="Blood type" value={null} required={false}
    options={[{ label: 'A+', value: 'A_POS' }]} onChange={vi.fn()} />);
  expect(container.querySelector('.mb-4 > div').className).not.toContain('[&_[data-label]>span]:block');
});
