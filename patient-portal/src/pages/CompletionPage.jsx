import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { ArrowLeft, CheckCircle, Edit05, FileDownload02 } from '@untitledui/icons';
import { getPedigreeDiagram } from '../api/client';
import { Button } from '../components/base/buttons/button';
import { BadgeWithIcon } from '../components/base/badges/badges';

const DOWNLOAD_FORMATS = ['svg', 'png', 'pdf'];

// The real destination once an assessment is done: the rendered pedigree itself, not just a
// confirmation message. A diagram-render failure already implies an export failure, so this one
// fetch drives both the content and the loading/error state - no separate validation call.
export default function CompletionPage({ sessionId }) {
  const [objectUrl, setObjectUrl] = useState(null);
  const [error, setError] = useState(null);
  const [downloadError, setDownloadError] = useState(null);
  const [retryCount, setRetryCount] = useState(0);

  // Mirrors PedigreeViewerModal's cancelled/currentUrl pattern: a retry or a sessionId change
  // starts a new fetch, and the object URL from whichever fetch is still in flight when this
  // effect tears down is revoked rather than leaked or left to clobber a newer one.
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
  }, [sessionId, retryCount]);

  const retry = () => {
    setError(null);
    setObjectUrl(null);
    setRetryCount((c) => c + 1);
  };

  const download = (format) => {
    setDownloadError(null);
    getPedigreeDiagram(sessionId, format).then(
      (blob) => {
        const url = URL.createObjectURL(blob);
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = `pedigree.${format}`;
        document.body.appendChild(anchor);
        anchor.click();
        anchor.remove();
        setTimeout(() => URL.revokeObjectURL(url), 0);
      },
      (err) => setDownloadError(err.message ?? 'The download could not be completed.'),
    );
  };

  if (error) {
    return (
      <div className="flex min-h-screen items-center justify-center p-4">
        <div className="w-full max-w-md rounded-2xl border border-secondary bg-primary p-8 text-center shadow-lg">
          <p className="mb-4 text-sm text-error-primary">{error}</p>
          <Button type="button" color="primary" onClick={retry}>
            Try again
          </Button>
        </div>
      </div>
    );
  }

  if (!objectUrl) {
    return (
      <div className="flex min-h-screen items-center justify-center p-4">
        <p className="text-sm text-tertiary">Rendering your pedigree...</p>
      </div>
    );
  }

  return (
    <div className="min-h-screen">
      {/* A soft brand-tinted header band gives this "destination" page its own identity instead
          of reusing the plain white-card look of every in-progress screen. */}
      <div className="border-b border-secondary bg-brand-50 px-4 py-10 md:px-8">
        <div className="mx-auto max-w-4xl">
          <Link
            to="/"
            className="mb-6 inline-flex items-center gap-1.5 text-sm font-medium text-tertiary hover:text-secondary"
          >
            <ArrowLeft className="size-4" />
            All sessions
          </Link>

          <BadgeWithIcon type="pill-color" color="success" size="md" iconLeading={CheckCircle} className="mb-4">
            Assessment complete
          </BadgeWithIcon>

          <h1 className="text-display-sm font-semibold text-primary">Your pedigree</h1>
          <p className="mt-2 max-w-xl text-md text-tertiary">
            Here's the family pedigree built from everything you and your family members answered.
            Review it below, or download a copy to share with your care team.
          </p>
        </div>
      </div>

      <div className="mx-auto max-w-4xl px-4 py-8 md:px-8">
        <div className="overflow-hidden rounded-2xl border border-secondary bg-primary shadow-lg">
          <div className="flex flex-wrap items-center justify-between gap-3 border-b border-secondary px-6 py-4">
            <h2 className="text-sm font-semibold text-primary">Family pedigree diagram</h2>
            <Link
              to={`/assessment/${sessionId}`}
              className="inline-flex items-center gap-1.5 text-sm font-semibold text-brand-secondary hover:text-brand-secondary_hover"
            >
              <Edit05 className="size-4" />
              Edit answers
            </Link>
          </div>

          <div className="flex justify-center bg-secondary/40 p-6">
            <img src={objectUrl} alt="Pedigree diagram" className="max-h-[65vh] max-w-full w-auto rounded-lg bg-primary" />
          </div>

          <div className="border-t border-secondary px-6 py-5">
            {downloadError && <p className="mb-3 text-sm text-error-primary">{downloadError}</p>}

            <div className="flex flex-wrap items-center gap-3">
              <span className="text-sm font-medium text-secondary">Download as:</span>
              {DOWNLOAD_FORMATS.map((format) => (
                <Button
                  key={format}
                  type="button"
                  color="secondary"
                  size="sm"
                  iconLeading={FileDownload02}
                  onClick={() => download(format)}
                >
                  {format.toUpperCase()}
                </Button>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
