// No persistent top navbar - the app's only multi-section context (the assessment flow) carries
// its own brand heading inside TemplateView's sidebar instead (see TemplateView.jsx). This is now
// just a thin, page-agnostic wrapper so every route shares the same background token.
export default function AppShell({ children }) {
  return (
    <div className="min-h-screen bg-primary">
      <main>{children}</main>
    </div>
  );
}
