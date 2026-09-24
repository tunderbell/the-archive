/**
 * -----------------------------------------------------------------------------
 * main.tsx - Root Entry for The Archive - APEX Console
 * -----------------------------------------------------------------------------
 * Mounts the React 18 application tree wrapped in the ScreenProvider context,
 * ensuring all multi-screen state and layout preferences are accessible throughout
 * the component hierarchy.
 * -----------------------------------------------------------------------------
 */

import React from 'react';
import ReactDOM from 'react-dom/client';
// Bundled directly by Vite's module graph to ensure stylesheet is injected
import 'dockview-core/dist/styles/dockview.css';
import './index.css';
import { App } from './App';
import { ScreenProvider } from './context/ScreenContext';

ReactDOM.createRoot(document.getElementById('root') as HTMLElement).render(
  <React.StrictMode>
    <ScreenProvider>
      <App />
    </ScreenProvider>
  </React.StrictMode>
);
