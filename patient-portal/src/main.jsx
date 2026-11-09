import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { ThemeProvider } from './providers/theme-provider'
import './styles/globals.css'
import App from './App.jsx'

createRoot(document.getElementById('root')).render(
  <StrictMode>
    {/* This app only ships a light theme - no dark-mode toggle exists anywhere in the UI - so the
        provider is pinned to "light" rather than "system", which would otherwise follow the
        visitor's OS preference. */}
    <ThemeProvider defaultTheme="light">
      <App />
    </ThemeProvider>
  </StrictMode>,
)
