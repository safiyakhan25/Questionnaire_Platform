import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import CountryOfOriginField from './CountryOfOriginField';

// TemplateView's handleAnswerChange submits the new value and then calls refresh(), which
// re-renders this component with an updated `value` prop in place - the component must survive
// that as the same instance, not remount, or the user's focus is yanked out of the search input
// after every single selection.
it('keeps focus in the search input when value changes after a selection round-trips through the backend', async () => {
  const user = userEvent.setup();
  const options = [{ label: 'India', value: 'IN' }, { label: 'Indonesia', value: 'ID' }];
  const onChange = vi.fn();
  const { rerender } = render(
    <CountryOfOriginField fieldId="q" label="Country of origin" value={[]} required={false} options={options} onChange={onChange} />,
  );

  // Query by role, not label text: once the popover opens, its "Suggestions" listbox is also
  // labelled by this field's label, so getByLabelText would ambiguously match both.
  const input = screen.getByRole('combobox');
  await user.click(input);
  expect(input).toHaveFocus();

  rerender(
    <CountryOfOriginField fieldId="q" label="Country of origin" value={['IN']} required={false} options={options} onChange={onChange} />,
  );

  expect(screen.getByRole('combobox')).toHaveFocus();
});
