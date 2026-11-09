import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { getPedigreeDiagram } from '../api/client';
import { ModalOverlay, Modal, Dialog } from './application/modals/modal';
import { Button } from './base/buttons/button';

// The in-progress "quick peek" at the pedigree - a dismissible overlay, not a page navigation, so
// answering can continue right where it left off. Always fetches fresh on open (never caches the
// last image) since the underlying data may have changed since it was last opened. Mounted/
// unmounted by the parent (TemplateView) rather than toggled via a persistent isOpen prop, so
// ModalOverlay's own isOpen is always true while this component exists - onOpenChange still gets
// wired to onClose so the free Escape-key/backdrop-click dismissal ModalOverlay provides works too.
export default function PedigreeViewerModal({ sessionId, onClose }) {
  const navigate = useNavigate();
  const [objectUrl, setObjectUrl] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    let currentUrl = null;
    let cancelled = false;

    getPedigreeDiagram(sessionId).then(
      (blob) => {
        if (cancelled) return;
        currentUrl = URL.createObjectURL(blob);
        setObjectUrl(currentUrl);
      },
      (err) => {
        if (cancelled) return;
        setError(err.message ?? 'The pedigree could not be rendered.');
      },
    );

    return () => {
      cancelled = true;
      if (currentUrl) URL.revokeObjectURL(currentUrl);
    };
  }, [sessionId]);

  return (
    <ModalOverlay isOpen isDismissable onOpenChange={(isOpen) => !isOpen && onClose()}>
      <Modal className="max-w-3xl">
        <Dialog className="p-4">
          <div className="mb-2 flex items-center justify-between gap-3">
            <Button type="button" color="secondary" size="sm" onClick={() => navigate(`/completion/${sessionId}`)}>
              View in full
            </Button>
            <Button type="button" color="tertiary" size="sm" onClick={onClose}>
              Close
            </Button>
          </div>
          {error && <p className="text-sm text-error-primary">{error}</p>}
          {!error && !objectUrl && <p className="text-sm text-tertiary">Loading...</p>}
          {!error && objectUrl && (
            <div className="flex justify-center">
              <img src={objectUrl} alt="Pedigree diagram" className="max-w-full" />
            </div>
          )}
        </Dialog>
      </Modal>
    </ModalOverlay>
  );
}
