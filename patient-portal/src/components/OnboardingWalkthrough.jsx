import { ModalOverlay, Modal, Dialog } from './application/modals/modal';
import { Button } from './base/buttons/button';

const STEPS = [
  'Answer questions here, in the current section of your assessment.',
  'Add a family member here, to fill in their health history too.',
  'Go back and edit here, any time you need to change an earlier answer.',
];

export default function OnboardingWalkthrough({ onDismiss }) {
  const dismiss = () => {
    try {
      localStorage.setItem('qp_onboarding_seen', 'true');
    } catch {
      // ignore storage failures (private browsing, etc.)
    }
    onDismiss();
  };

  return (
    <ModalOverlay isOpen isDismissable onOpenChange={(isOpen) => !isOpen && dismiss()}>
      <Modal className="max-w-sm">
        <Dialog className="p-6">
          <h2 className="mb-4 text-lg font-semibold text-primary">Welcome</h2>
          <ul className="mb-6 space-y-3 text-sm text-secondary">
            {STEPS.map((step) => (
              <li key={step}>{step}</li>
            ))}
          </ul>
          <Button type="button" color="primary" className="w-full" onClick={dismiss}>
            Got it
          </Button>
        </Dialog>
      </Modal>
    </ModalOverlay>
  );
}
