import { render, screen, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import QuestionField from './QuestionField';

beforeEach(() => {
  vi.useFakeTimers();
  vi.setSystemTime(new Date('2026-06-15'));
});

afterEach(() => {
  vi.useRealTimers();
});

it('renders a TEXT question as a text input and reports string changes', () => {
  const onChange = vi.fn();
  render(<QuestionField question={{ id: '1', answerKey: 'first_name', text: 'First name', type: 'TEXT', options: null, validation: { required: true }, visible: true, value: null }} onChange={onChange} />);

  fireEvent.change(screen.getByLabelText(/^First name/), { target: { value: 'Sarah' } });
  expect(onChange).toHaveBeenCalledWith('Sarah');
});

it('renders a NUMBER question as a number input and reports numeric changes', () => {
  const onChange = vi.fn();
  render(<QuestionField question={{ id: '1b', answerKey: 'age', text: 'Age', type: 'NUMBER', options: null, validation: null, visible: true, value: null }} onChange={onChange} />);

  fireEvent.change(screen.getByLabelText(/^Age/), { target: { value: '42' } });
  expect(onChange).toHaveBeenCalledWith(42);
});

it('renders a DATE question as a date input', () => {
  const onChange = vi.fn();
  render(<QuestionField question={{ id: '1c', answerKey: 'dob', text: 'Date of birth', type: 'DATE', options: null, validation: null, visible: true, value: null }} onChange={onChange} />);

  fireEvent.change(screen.getByLabelText(/^Date of birth/), { target: { value: '2024-01-15' } });
  expect(onChange).toHaveBeenCalledWith('2024-01-15');
});

it('shows the computed current age under birth_date once it has a value', () => {
  render(<QuestionField question={{ id: '1d', answerKey: 'birth_date', text: 'Date of birth (if known)', type: 'DATE', options: null, validation: null, visible: true, value: '1996-01-10' }} onChange={vi.fn()} />);

  const ageHint = screen.getByText('Current age: 30 years');
  expect(ageHint).toBeInTheDocument();
  expect(ageHint).not.toHaveClass('text-gray-500');
});

it('does not show an age hint for a DATE question other than birth_date', () => {
  render(<QuestionField question={{ id: '1e', answerKey: 'deceased_date_time', text: 'Date of death', type: 'DATE', options: null, validation: null, visible: true, value: '1996-01-10' }} onChange={vi.fn()} />);

  expect(screen.queryByText(/Current age/)).not.toBeInTheDocument();
});

it("renders RADIO as a dropdown and reports the option's raw value, not its label", () => {
  const onChange = vi.fn();
  render(<QuestionField question={{ id: '2', answerKey: 'sex_assigned_at_birth', text: 'Sex', type: 'RADIO', options: [{ label: 'Male', value: 'MALE' }, { label: 'Female', value: 'FEMALE' }], validation: null, visible: true, value: null }} onChange={onChange} />);

  fireEvent.change(screen.getByLabelText(/^Sex/), { target: { value: 'FEMALE' } });
  expect(onChange).toHaveBeenCalledWith('FEMALE');
});

it('renders DROPDOWN options from the options array and reports the selected raw value', () => {
  const onChange = vi.fn();
  render(<QuestionField question={{ id: '2b', answerKey: 'blood_type', text: 'Blood type', type: 'DROPDOWN', options: [{ label: 'A+', value: 'A_POS' }, { label: 'O-', value: 'O_NEG' }], validation: null, visible: true, value: null }} onChange={onChange} />);

  fireEvent.change(screen.getByLabelText(/^Blood type/), { target: { value: 'O_NEG' } });
  expect(onChange).toHaveBeenCalledWith('O_NEG');
});

it('renders CHECKBOX as multi-select and always reports an array, even for one selection', () => {
  const onChange = vi.fn();
  render(<QuestionField question={{ id: '3', answerKey: 'ethnicity', text: 'Ethnicity', type: 'CHECKBOX', options: [{ label: 'A', value: 'A' }, { label: 'B', value: 'B' }], validation: null, visible: true, value: null }} onChange={onChange} />);

  fireEvent.click(screen.getByLabelText('A'));
  expect(onChange).toHaveBeenCalledWith(['A']);
});

it('CHECKBOX reports a full list when more than one option is selected', () => {
  const onChange = vi.fn();
  render(<QuestionField question={{ id: '3b', answerKey: 'ethnicity', text: 'Ethnicity', type: 'CHECKBOX', options: [{ label: 'A', value: 'A' }, { label: 'B', value: 'B' }], validation: null, visible: true, value: ['A'] }} onChange={onChange} />);

  fireEvent.click(screen.getByLabelText('B'));
  expect(onChange).toHaveBeenCalledWith(['A', 'B']);
});

it('CHECKBOX unchecking an option removes it from the reported array', () => {
  const onChange = vi.fn();
  render(<QuestionField question={{ id: '3c', answerKey: 'ethnicity', text: 'Ethnicity', type: 'CHECKBOX', options: [{ label: 'A', value: 'A' }, { label: 'B', value: 'B' }], validation: null, visible: true, value: ['A', 'B'] }} onChange={onChange} />);

  fireEvent.click(screen.getByLabelText('A'));
  expect(onChange).toHaveBeenCalledWith(['B']);
});

it('renders country_of_origin as a searchable multi-select instead of a checkbox list', async () => {
  // This file's top-level beforeEach enables fake timers for the age-calculation tests above;
  // React Aria's ComboBox popover drives its open/position state off real rAF-scheduled work that
  // fake timers never advance, so this one interaction-heavy test needs real timers.
  vi.useRealTimers();
  const user = userEvent.setup();
  const onChange = vi.fn();
  const options = [{ label: 'India', value: 'IN' }, { label: 'Indonesia', value: 'ID' }];
  render(<QuestionField question={{ id: '6', answerKey: 'country_of_origin', text: 'Country of origin', type: 'CHECKBOX', options, validation: null, visible: true, value: [] }} onChange={onChange} />);

  const input = screen.getByLabelText(/^Country of origin/);
  await user.type(input, 'ind');
  await user.click(await screen.findByText('India'));

  expect(onChange).toHaveBeenCalledWith(['IN']);
  expect(screen.queryByRole('checkbox')).not.toBeInTheDocument();
});

it('does not render anything when visible is false', () => {
  const { container } = render(<QuestionField question={{ id: '4', answerKey: 'age', text: 'Age', type: 'NUMBER', options: null, validation: null, visible: false, value: null }} onChange={vi.fn()} />);
  expect(container).toBeEmptyDOMElement();
});

it('marks a required question as required on the field itself', () => {
  render(<QuestionField question={{ id: '5', answerKey: 'first_name', text: 'First name', type: 'TEXT', options: null, validation: { required: true }, visible: true, value: null }} onChange={vi.fn()} />);
  expect(screen.getByLabelText(/^First name/)).toBeRequired();
});

it('does not mark an optional question as required', () => {
  render(<QuestionField question={{ id: '5b', answerKey: 'nickname', text: 'Nickname', type: 'TEXT', options: null, validation: { required: false }, visible: true, value: null }} onChange={vi.fn()} />);
  expect(screen.getByLabelText(/^Nickname/)).not.toBeRequired();
});

it('renders a STRUCTURED question via StructuredQuestionField, passing sessionId and the subject id through', () => {
  const question = { id: 'q1', answerKey: 'twinship', text: 'Twinship', type: 'STRUCTURED',
    fieldConfig: { shape: 'object', fields: [{ name: 'twinType', label: 'Type', type: 'TEXT', required: true }] },
    validation: null, visible: true, value: null };
  render(<QuestionField question={question} onChange={vi.fn()} sessionId="s1" familyMemberId="fm1" />);
  fireEvent.click(screen.getByLabelText(/yes/i));
  expect(screen.getByLabelText(/^Type/)).toBeInTheDocument();
});
