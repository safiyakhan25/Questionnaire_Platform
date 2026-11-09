import { useState } from 'react';
import TemplateView from '../components/TemplateView';
import OnboardingWalkthrough from '../components/OnboardingWalkthrough';

function onboardingAlreadySeen() {
  try {
    return localStorage.getItem('qp_onboarding_seen') === 'true';
  } catch {
    return false;
  }
}

export default function AssessmentPage({ sessionId, onCompleted }) {
  const [familyMemberId, setFamilyMemberId] = useState(null);
  const [subjectLabel, setSubjectLabel] = useState('you');
  const [showOnboarding, setShowOnboarding] = useState(() => !onboardingAlreadySeen());

  const backToProband = () => {
    setFamilyMemberId(null);
    setSubjectLabel('you');
  };

  return (
    <>
      {/* A real Modal/Dialog overlay (Task 11) - no longer needs the flex/padding wrapper this
          page had back when it rendered as an inline full-screen layer alongside TemplateView. */}
      {showOnboarding && <OnboardingWalkthrough onDismiss={() => setShowOnboarding(false)} />}
      <TemplateView
        sessionId={sessionId}
        familyMemberId={familyMemberId}
        subjectLabel={subjectLabel}
        onCompleted={() => onCompleted?.(sessionId)}
        onFamilyMemberPassEnded={backToProband}
        onBackToFamilyMembers={backToProband}
        onContinueFamilyMember={(newFamilyMemberId, label) => {
          setFamilyMemberId(newFamilyMemberId);
          setSubjectLabel(label);
        }}
      />
    </>
  );
}
