import { BrowserRouter, Routes, Route, Navigate, useNavigate, useParams } from 'react-router-dom';
import AppShell from './components/layout/AppShell';
import SessionListPage from './pages/SessionListPage';
import AssessmentPage from './pages/AssessmentPage';
import CompletionPage from './pages/CompletionPage';

function AssessmentRoute() {
  const { sessionId } = useParams();
  const navigate = useNavigate();
  return (
    <AssessmentPage
      sessionId={sessionId}
      onCompleted={() => navigate(`/completion/${sessionId}`)}
    />
  );
}

function CompletionRoute() {
  const { sessionId } = useParams();
  return <CompletionPage sessionId={sessionId} />;
}

function AppRoutes() {
  return (
    <Routes>
      <Route path="/" element={<SessionListPage />} />
      <Route path="/assessment/:sessionId" element={<AssessmentRoute />} />
      <Route path="/completion/:sessionId" element={<CompletionRoute />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}

export default function App() {
  return (
    <BrowserRouter>
      <AppShell>
        <AppRoutes />
      </AppShell>
    </BrowserRouter>
  );
}
