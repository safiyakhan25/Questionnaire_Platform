import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import QuestionField from './QuestionField';
import VisitedTemplatesNav from './VisitedTemplatesNav';
import FamilyMemberHub from './FamilyMemberHub';
import PedigreeViewerModal from './PedigreeViewerModal';
import { Button } from './base/buttons/button';
import { getCurrentTemplate, submitAnswer, nextTemplate, resumeAtTemplate } from '../api/client';

export default function TemplateView({ sessionId, familyMemberId, subjectLabel, onCompleted, onFamilyMemberPassEnded, onBackToFamilyMembers, onContinueFamilyMember }) {
  const [template, setTemplate] = useState(null);
  const [nextError, setNextError] = useState(null);
  const [answerError, setAnswerError] = useState(null);
  const [showPedigreeViewer, setShowPedigreeViewer] = useState(false);
  // The templateIds visited so far on this subject's own walk (push on every forward/jump move,
  // pop on Previous) - lets a "Previous" button undo one step without the backend needing its own
  // concept of "the template before this one" (the fixed template order can branch via skip-logic,
  // so "previous in the list" isn't always "the one the user was just on").
  const [history, setHistory] = useState([]);
  // Bumped whenever sessionId/familyMemberId changes, so a response from a request started
  // under a previous subject can never overwrite the template for the subject now on screen.
  const requestTokenRef = useRef(0);

  const refresh = () => {
    const token = requestTokenRef.current;
    return getCurrentTemplate(sessionId, familyMemberId).then((result) => {
      if (requestTokenRef.current === token) setTemplate(result);
    });
  };

  useEffect(() => {
    requestTokenRef.current += 1;
    setTemplate(null);
    setNextError(null);
    setAnswerError(null);
    setHistory([]);
    refresh();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sessionId, familyMemberId]);

  if (!template) return <div className="p-4">Loading...</div>;

  const handleAnswerChange = async (questionId, value) => {
    const token = requestTokenRef.current;
    setAnswerError(null);
    try {
      await submitAnswer(sessionId, { questionId, value, familyMemberId });
      if (requestTokenRef.current === token) await refresh();
    } catch (err) {
      if (requestTokenRef.current === token) setAnswerError(err.message ?? 'Your last answer could not be saved.');
    }
  };

  const handleNext = async () => {
    const token = requestTokenRef.current;
    setNextError(null);
    try {
      const result = await nextTemplate(sessionId, familyMemberId);
      if (requestTokenRef.current !== token) return;
      if (result.completed && result.familyMemberId == null) {
        // The whole session is done - only the proband's own walk exhausting sets this.
        onCompleted();
        return;
      }
      if (result.completed) {
        // Just this family member's own pass finished - the proband's position was never
        // touched by it, so there's nothing to "return" to, just step back to the hub.
        onFamilyMemberPassEnded();
        return;
      }
      setHistory((h) => [...h, template.templateId]);
      setTemplate(result);
    } catch (err) {
      if (requestTokenRef.current !== token) return;
      if (err.status === 409) {
        setNextError(err.message);
      } else {
        throw err;
      }
    }
  };

  const handlePrevious = async () => {
    if (history.length === 0) return;
    const token = requestTokenRef.current;
    const previousTemplateId = history[history.length - 1];
    setNextError(null);
    try {
      const result = await resumeAtTemplate(sessionId, previousTemplateId, familyMemberId);
      if (requestTokenRef.current !== token) return;
      setHistory((h) => h.slice(0, -1));
      setTemplate(result);
    } catch (err) {
      if (requestTokenRef.current !== token) return;
      setNextError(err.message ?? 'Could not go back to the previous section.');
    }
  };

  // Jumping via the sidebar is also a step worth undoing with Previous, same as a forward Next.
  const handleResumed = (result) => {
    if (template?.templateId != null) setHistory((h) => [...h, template.templateId]);
    setTemplate(result);
  };

  return (
    <div className="md:flex md:items-start">
      {/* Flush to the viewport's left and top edges on desktop (no page padding wraps this column,
          and no app-wide navbar sits above it) - a card with its own margin on mobile, where
          there's no "left of the sidebar" to be flush against. Carries the app's own brand heading
          at its top, since this is the only place in the app that still has one. */}
      <div className="m-4 rounded-xl p-4 md:m-0 md:w-56 md:shrink-0 md:rounded-none md:p-6 md:sticky md:top-0 md:h-screen md:flex md:flex-col bg-brand-solid">
        <div>
          <Link to="/" className="mb-4 block text-lg font-semibold text-white">
            QuestionnairePlatform
          </Link>

          <VisitedTemplatesNav
            sessionId={sessionId}
            familyMemberId={familyMemberId}
            refreshKey={`${familyMemberId}:${template.templateCode}`}
            onResumed={handleResumed}
          />
        </div>

        <div className="mt-4 md:mt-auto">
          <Button type="button" color="secondary" size="md" className="w-full" onClick={() => setShowPedigreeViewer(true)}>
            Pedigree Viewer
          </Button>
        </div>
      </div>

      <div className="p-4 md:flex-1 md:min-w-0 md:p-8">
        {familyMemberId != null && (
          <div className="mb-4 flex items-center justify-between gap-3 rounded-xl border border-secondary bg-secondary px-4 py-3 text-sm">
            <span className="text-secondary">You are now answering for: {subjectLabel}</span>
            <Button type="button" color="link-gray" size="sm" onClick={onBackToFamilyMembers}>
              ← Back to Family Members
            </Button>
          </div>
        )}

        {template.completed ? (
          <p className="text-secondary">This assessment is complete. Select a section on the left to review or edit it.</p>
        ) : template.templateCode === 'FAMILY_MEMBER_HUB' ? (
          <FamilyMemberHub
            sessionId={sessionId}
            onContinue={onContinueFamilyMember}
            prevSlot={
              history.length > 0 && (
                <Button type="button" color="secondary" size="md" onClick={handlePrevious}>
                  Previous
                </Button>
              )
            }
            nextSlot={
              <Button color="primary" size="md" onClick={handleNext}>
                Next
              </Button>
            }
          />
        ) : (
          <>
            <h2 className="mb-4 text-display-xs font-semibold text-primary">{template.templateName ?? template.templateCode}</h2>

            {(template.questions ?? []).map((question) => (
              <QuestionField
                key={question.id}
                question={question}
                onChange={(value) => handleAnswerChange(question.id, value)}
                sessionId={sessionId}
                familyMemberId={familyMemberId}
              />
            ))}

            {answerError && <p className="mb-4 text-sm text-error-primary">{answerError}</p>}
          </>
        )}

        {nextError && <p className="mb-4 text-sm text-error-primary">{nextError}</p>}

        {template.templateCode !== 'FAMILY_MEMBER_HUB' && !template.completed && (
          <div className="mt-2 flex items-center gap-3">
            {history.length > 0 && (
              <Button type="button" color="secondary" size="md" onClick={handlePrevious}>
                Previous
              </Button>
            )}
            <Button color="primary" size="md" onClick={handleNext}>
              Next
            </Button>
          </div>
        )}
      </div>

      {showPedigreeViewer && (
        <PedigreeViewerModal sessionId={sessionId} onClose={() => setShowPedigreeViewer(false)} />
      )}
    </div>
  );
}
