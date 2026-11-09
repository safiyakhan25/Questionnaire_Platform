import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import AppShell from './AppShell';

it('renders its children with no persistent navbar', () => {
  render(
    <MemoryRouter>
      <AppShell>
        <p>Page content</p>
      </AppShell>
    </MemoryRouter>,
  );

  expect(screen.getByText('Page content')).toBeInTheDocument();
  expect(screen.queryByRole('banner')).not.toBeInTheDocument();
});
