import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { vi } from 'vitest';
import VisitedTemplatesNav from './VisitedTemplatesNav';
import * as client from '../api/client';

it('lists visited templates and resumes the one clicked', async () => {
  vi.spyOn(client, 'getVisitedTemplates').mockResolvedValue([
    { templateId: 't1', templateCode: 'DEMOGRAPHICS', templateName: 'Demographics' },
    { templateId: 't2', templateCode: 'PARENTS', templateName: 'Parents' },
  ]);
  const resumed = { templateCode: 'DEMOGRAPHICS', questions: [] };
  vi.spyOn(client, 'resumeAtTemplate').mockResolvedValue(resumed);
  const onResumed = vi.fn();

  render(<VisitedTemplatesNav sessionId="s1" familyMemberId={null} onResumed={onResumed} />);
  fireEvent.click(await screen.findByText('Demographics'));

  await waitFor(() => expect(client.resumeAtTemplate).toHaveBeenCalledWith('s1', 't1', null));
  expect(onResumed).toHaveBeenCalledWith(resumed);
});

it('lists and resumes a family member\'s visited templates when familyMemberId is set', async () => {
  vi.spyOn(client, 'getVisitedTemplates').mockResolvedValue([
    { templateId: 't3', templateCode: 'FAMILY_INFO', templateName: 'Family Info' },
  ]);
  vi.spyOn(client, 'resumeAtTemplate').mockResolvedValue({ templateCode: 'FAMILY_INFO', questions: [] });

  render(<VisitedTemplatesNav sessionId="s1" familyMemberId="fm1" onResumed={vi.fn()} />);
  fireEvent.click(await screen.findByText('Family Info'));

  expect(client.getVisitedTemplates).toHaveBeenCalledWith('s1', 'fm1');
  await waitFor(() => expect(client.resumeAtTemplate).toHaveBeenCalledWith('s1', 't3', 'fm1'));
});

it('re-fetches the visited list so a newly visited section shows up without a full page reload', async () => {
  vi.spyOn(client, 'getVisitedTemplates')
    .mockResolvedValueOnce([{ templateId: 't1', templateCode: 'DEMOGRAPHICS', templateName: 'Demographics' }])
    .mockResolvedValueOnce([
      { templateId: 't1', templateCode: 'DEMOGRAPHICS', templateName: 'Demographics' },
      { templateId: 't2', templateCode: 'PARENTS', templateName: 'Parents' },
    ]);

  const { rerender } = render(<VisitedTemplatesNav sessionId="s1" onResumed={vi.fn()} refreshKey={1} />);
  await screen.findByText('Demographics');

  rerender(<VisitedTemplatesNav sessionId="s1" onResumed={vi.fn()} refreshKey={2} />);

  await waitFor(() => expect(screen.getByText('Parents')).toBeInTheDocument());
});
