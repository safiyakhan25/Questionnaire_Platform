import { useEffect, useState } from 'react';
import { getVisitedTemplates, resumeAtTemplate } from '../api/client';
import { Button } from './base/buttons/button';

export default function VisitedTemplatesNav({ sessionId, familyMemberId, onResumed, refreshKey }) {
  const [templates, setTemplates] = useState([]);

  useEffect(() => {
    getVisitedTemplates(sessionId, familyMemberId).then(setTemplates);
  }, [sessionId, familyMemberId, refreshKey]);

  const handleClick = async (templateId) => {
    const result = await resumeAtTemplate(sessionId, templateId, familyMemberId);
    onResumed(result);
  };

  // Rendered on the solid-brand-purple sidebar panel (see TemplateView.jsx) - Button's own
  // `link-gray` colors assume a light page background, so its text color is forced to white here
  // with the `!` important modifier (a plain conflicting utility class wouldn't reliably win,
  // since same-specificity Tailwind utilities resolve by generation order, not by which one is
  // passed last).
  return (
    <nav className="mb-4">
      <h3 className="mb-2 text-sm font-semibold text-white/70">Go back and edit</h3>
      <ul className="space-y-1">
        {templates.map((t) => (
          <li key={t.templateId}>
            <Button
              type="button"
              color="link-gray"
              size="sm"
              className="text-white! hover:text-white/80!"
              onClick={() => handleClick(t.templateId)}
            >
              {t.templateName}
            </Button>
          </li>
        ))}
      </ul>
    </nav>
  );
}
