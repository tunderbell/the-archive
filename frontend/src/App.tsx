/**
 * -----------------------------------------------------------------------------
 * App.tsx - The Archive APEX Console Shell
 * -----------------------------------------------------------------------------
 * The primary container orchestrating:
 * 1. TopHeaderBar: Screen switching (SCRN: ...), [+ ADD] modal, status badges.
 * 2. LeftSidebar: Monospace quick-launch rail (SDBR, BFRS, VLT, TERM, etc.).
 * 3. CommandBar: Omnipresent APEX CLI prompt positioned at TOP or BOTTOM.
 * 4. DockviewReact: Tiled buffer grid hosting resizable, draggable, tabbed panels.
 * -----------------------------------------------------------------------------
 */

import React, { useRef, useEffect, useCallback } from 'react';
import {
  DockviewReact,
  DockviewReadyEvent,
  DockviewApi,
} from 'dockview-react';
import { useScreens } from './context/ScreenContext';
import { TopHeaderBar } from './components/layout/TopHeaderBar';
import { LeftSidebar, AVAILABLE_BUFFERS } from './components/layout/LeftSidebar';
import { CommandBar } from './components/layout/CommandBar';

// Buffer Components
import { MediaVaultBuffer } from './components/buffers/MediaVaultBuffer';
import { SystemLogBuffer } from './components/buffers/SystemLogBuffer';
import { TerminalBuffer } from './components/buffers/TerminalBuffer';
import { ChatRoomBuffer } from './components/buffers/ChatRoomBuffer';
import { ScraperMonitorBuffer } from './components/buffers/ScraperMonitorBuffer';

// Map component identifiers directly to React buffer views
const BUFFER_COMPONENTS = {
  'media-vault': MediaVaultBuffer,
  'system-log': SystemLogBuffer,
  'terminal': TerminalBuffer,
  'chat-room': ChatRoomBuffer,
  'scraper-monitor': ScraperMonitorBuffer,
};

export const App: React.FC = () => {
  const {
    activeScreen,
    activeLayoutJson,
    saveActiveLayout,
    commandBarPosition,
    openBufferRequest,
    clearOpenBufferRequest,
  } = useScreens();

  const dockviewApiRef = useRef<DockviewApi | null>(null);
  const saveTimeoutRef = useRef<number | null>(null);
  const prevScreenRef = useRef<string>(activeScreen);
  const isInitializedRef = useRef<boolean>(false);

  // Initialize the 4-tile default dashboard layout
  const initDefaultDashboard = useCallback((api: DockviewApi) => {
    try {
      api.clear();

      // Panel 1: Top-Left Media Vault
      const p1 = api.addPanel({
        id: 'vault-panel',
        component: 'media-vault',
        title: 'MEDIA VAULT VLT',
      });

      // Panel 2: Top-Right System Log (split right of p1)
      api.addPanel({
        id: 'log-panel',
        component: 'system-log',
        title: 'SYSTEM LOG SYS',
        position: { referencePanel: p1, direction: 'right' },
      });

      // Panel 3: Bottom-Left Interactive Terminal (split below vault p1)
      const p3 = api.addPanel({
        id: 'terminal-panel',
        component: 'terminal',
        title: 'TERMINAL XTRM',
        position: { referencePanel: p1, direction: 'below' },
      });

      // Panel 4: Bottom-Right Chat Room (split right of terminal p3)
      api.addPanel({
        id: 'chat-panel',
        component: 'chat-room',
        title: 'GLOBAL CHAT COMP',
        position: { referencePanel: p3, direction: 'right' },
      });
    } catch (err) {
      console.warn('[App] Error during initDefaultDashboard:', err);
    }
  }, []);

  // Handler when Dockview is ready
  const onReady = (event: DockviewReadyEvent) => {
    dockviewApiRef.current = event.api;

    if (activeLayoutJson) {
      try {
        event.api.fromJSON(JSON.parse(activeLayoutJson));
      } catch (err) {
        console.warn('[App] Failed to restore layout JSON, loading default:', err);
        initDefaultDashboard(event.api);
      }
    } else {
      initDefaultDashboard(event.api);
    }

    isInitializedRef.current = true;

    // Auto-save layout on user resize, drag, or split (debounced)
    event.api.onDidLayoutChange(() => {
      if (!isInitializedRef.current) return;
      if (saveTimeoutRef.current) window.clearTimeout(saveTimeoutRef.current);
      saveTimeoutRef.current = window.setTimeout(() => {
        if (dockviewApiRef.current) {
          const serialized = JSON.stringify(dockviewApiRef.current.toJSON());
          saveActiveLayout(serialized);
        }
      }, 1500);
    });
  };

  // Re-load layout ONLY when user actively switches to a different screen
  useEffect(() => {
    const api = dockviewApiRef.current;
    if (!api || !isInitializedRef.current) return;

    if (prevScreenRef.current !== activeScreen) {
      prevScreenRef.current = activeScreen;
      if (activeLayoutJson) {
        try {
          api.clear();
          api.fromJSON(JSON.parse(activeLayoutJson));
        } catch {
          initDefaultDashboard(api);
        }
      } else {
        initDefaultDashboard(api);
      }
    }
  }, [activeScreen, activeLayoutJson, initDefaultDashboard]);

  // Handle spawn buffer requests from LeftSidebar or BFRS catalog
  useEffect(() => {
    if (!openBufferRequest || !dockviewApiRef.current) return;

    const api = dockviewApiRef.current;
    const bufMeta = AVAILABLE_BUFFERS.find((b) => b.id === openBufferRequest);
    const title = bufMeta ? `${bufMeta.name.toUpperCase()} ${bufMeta.code}` : openBufferRequest.toUpperCase();

    // Check if panel already exists; if so, focus it
    const existing = api.getPanel(openBufferRequest);
    if (existing) {
      existing.api.setActive();
    } else {
      api.addPanel({
        id: `${openBufferRequest}-${Date.now()}`,
        component: openBufferRequest,
        title,
      });
    }

    clearOpenBufferRequest();
  }, [openBufferRequest, clearOpenBufferRequest]);

  return (
    <div className="h-screen w-screen flex flex-col bg-[#0a0d11] text-[#e2e8f0] overflow-hidden select-none font-sans">
      {/* 1. APEX Top Navigation Bar */}
      <TopHeaderBar />

      {/* 2. Top-Docked Command Bar (if preference is TOP) */}
      {commandBarPosition === 'TOP' && <CommandBar />}

      {/* 3. Main Workspace Row (Sidebar + Tiled Dockview Canvas) */}
      <div className="flex-1 flex overflow-hidden">
        {/* Left Monospace Buffer Rail */}
        <LeftSidebar />

        {/* Resizable, Draggable Dockview Canvas */}
        <main className="flex-1 relative overflow-hidden bg-[#0c0f13] h-full w-full">
          <DockviewReact
            className="dockview-theme-dark dockview-theme-apex h-full w-full"
            components={BUFFER_COMPONENTS}
            onReady={onReady}
          />
        </main>
      </div>

      {/* 4. Bottom-Docked Command Bar (if preference is BOTTOM) */}
      {commandBarPosition === 'BOTTOM' && <CommandBar />}
    </div>
  );
};
