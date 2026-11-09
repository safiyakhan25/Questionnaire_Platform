import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ClipboardCheck } from '@untitledui/icons';
import { getSessions, startAssessment } from '../api/client';
import { Button } from '../components/base/buttons/button';
import { Badge } from '../components/base/badges/badges';
import { EmptyState } from '../components/application/empty-state/empty-state';

export default function SessionListPage() {
  const [sessions, setSessions] = useState(null);
  const navigate = useNavigate();

  useEffect(() => {
    getSessions().then(setSessions);
  }, []);

  const openSession = async (session) => {
    if (session.status === 'COMPLETED') {
      navigate(`/completion/${session.sessionId}`);
      return;
    }
    const started = await startAssessment({ sessionId: session.sessionId });
    navigate(`/assessment/${started.sessionId}`);
  };

  const startNewSession = async () => {
    const started = await startAssessment({ sessionId: null });
    navigate(`/assessment/${started.sessionId}`);
  };

  if (!sessions) return <div className="p-8 text-tertiary">Loading...</div>;

  return (
    <div className="mx-auto max-w-2xl p-4 md:p-8">
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-display-xs font-semibold text-primary">Your assessments</h1>
        <Button onClick={startNewSession} color="primary" size="md">
          Start new session
        </Button>
      </div>

      {sessions.length === 0 ? (
        <EmptyState>
          <EmptyState.Header>
            <EmptyState.FeaturedIcon icon={ClipboardCheck} />
          </EmptyState.Header>
          <EmptyState.Content>
            <EmptyState.Title>No assessments yet</EmptyState.Title>
            <EmptyState.Description>Start your first assessment to begin.</EmptyState.Description>
          </EmptyState.Content>
        </EmptyState>
      ) : (
        <ul className="divide-y divide-secondary rounded-xl border border-secondary bg-primary">
          {sessions.map((session) => (
            <li key={session.sessionId} className="flex items-center justify-between gap-3 px-4 py-3">
              <Button type="button" color="link-gray" size="md" onClick={() => openSession(session)}>
                {session.displayName}
              </Button>
              <Badge color={session.status === 'COMPLETED' ? 'success' : 'brand'} size="sm">
                {session.status === 'COMPLETED' ? 'Completed' : 'In progress'}
              </Badge>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
