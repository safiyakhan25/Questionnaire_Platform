import { render, screen, fireEvent } from '@testing-library/react';
import { vi } from 'vitest';
import StructuredQuestionField from './StructuredQuestionField';

it('shape "object": defaults the toggle to No and submits null when the user leaves it at No', () => {
  const question = { fieldConfig: { shape: 'object', fields: [{ name: 'fromGender', label: 'From', type: 'TEXT', required: true }] }, value: null };
  render(<StructuredQuestionField question={question} sessionId="s1" excludeId={null} onChange={vi.fn()} />);
  expect(screen.queryByLabelText(/^From/)).not.toBeInTheDocument();
});

it('shape "object": a non-null existing value defaults the toggle to Yes and pre-fills fields', () => {
  const question = { fieldConfig: { shape: 'object', fields: [{ name: 'fromGender', label: 'From', type: 'TEXT', required: true }] }, value: { fromGender: 'MAN' } };
  render(<StructuredQuestionField question={question} sessionId="s1" excludeId={null} onChange={vi.fn()} />);
  expect(screen.getByLabelText(/^From/)).toHaveValue('MAN');
});

it('shape "object": switching to Yes then filling a field submits the assembled object', () => {
  const question = { fieldConfig: { shape: 'object', fields: [{ name: 'fromGender', label: 'From', type: 'TEXT', required: true }] }, value: null };
  const onChange = vi.fn();
  render(<StructuredQuestionField question={question} sessionId="s1" excludeId={null} onChange={onChange} />);
  fireEvent.click(screen.getByLabelText(/yes/i));
  fireEvent.change(screen.getByLabelText(/^From/), { target: { value: 'MAN' } });
  expect(onChange).toHaveBeenCalledWith({ fromGender: 'MAN' });
});

it('shape "object": does not submit until every required field is filled in', () => {
  const question = { fieldConfig: { shape: 'object', fields: [
    { name: 'fromGender', label: 'From', type: 'TEXT', required: true },
    { name: 'toGender', label: 'To', type: 'TEXT', required: true },
  ] }, value: null };
  const onChange = vi.fn();
  render(<StructuredQuestionField question={question} sessionId="s1" excludeId={null} onChange={onChange} />);
  fireEvent.click(screen.getByLabelText(/yes/i));
  fireEvent.change(screen.getByLabelText(/^From/), { target: { value: 'MAN' } });
  expect(onChange).not.toHaveBeenCalledWith(expect.objectContaining({ fromGender: 'MAN' }));
  fireEvent.change(screen.getByLabelText(/^To/), { target: { value: 'WOMAN' } });
  expect(onChange).toHaveBeenCalledWith({ fromGender: 'MAN', toGender: 'WOMAN' });
});

it('shape "object": switching to No then back to Yes does not resurrect the unsaved draft', () => {
  const question = { fieldConfig: { shape: 'object', fields: [{ name: 'fromGender', label: 'From', type: 'TEXT', required: true }] }, value: null };
  render(<StructuredQuestionField question={question} sessionId="s1" excludeId={null} onChange={vi.fn()} />);
  fireEvent.click(screen.getByLabelText(/yes/i));
  fireEvent.change(screen.getByLabelText(/^From/), { target: { value: 'MAN' } });
  fireEvent.click(screen.getByLabelText(/no/i));
  fireEvent.click(screen.getByLabelText(/yes/i));
  expect(screen.getByLabelText(/^From/)).toHaveValue('');
});

it('renders the question text as a heading', () => {
  const question = { text: 'Twinship', fieldConfig: { shape: 'list', fields: [{ name: 'x', label: 'X', type: 'TEXT' }] }, value: [] };
  render(<StructuredQuestionField question={question} sessionId="s1" excludeId={null} onChange={vi.fn()} />);
  expect(screen.getByText('Twinship')).toBeInTheDocument();
});

it('shape "list": renders with no apply-toggle and submits the row array directly once a row is complete', () => {
  const question = { fieldConfig: { shape: 'list', fields: [{ name: 'diseaseType', label: 'Disease name', type: 'TEXT', required: true }] }, value: [] };
  const onChange = vi.fn();
  render(<StructuredQuestionField question={question} sessionId="s1" excludeId={null} onChange={onChange} />);
  expect(screen.queryByLabelText(/yes/i)).not.toBeInTheDocument();
  fireEvent.click(screen.getByText(/add/i));
  expect(onChange).toHaveBeenCalledWith([]); // not yet complete - diseaseType is required and still blank
  fireEvent.change(screen.getByLabelText(/^Disease name/), { target: { value: 'Breast Cancer' } });
  expect(onChange).toHaveBeenCalledWith([{ diseaseType: 'Breast Cancer' }]);
});
