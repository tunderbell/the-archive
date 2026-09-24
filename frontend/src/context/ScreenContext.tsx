/**
 * -----------------------------------------------------------------------------
 * ScreenContext - Multi-Screen & Workspace State Engine
 * -----------------------------------------------------------------------------
 * Manages the state for the APEX multi-screen workspace architecture.
 *
 * Each "Screen" is an independent Dockview tiling arrangement saved as JSON in
 * the backend SQLite vault (UserLayout table). Users can switch screens via the
 * top bar dropdown or sidebar, click [+ ADD] to create new screens, and reposition
 * the command prompt between 'TOP' and 'BOTTOM'.
 * -----------------------------------------------------------------------------
 */

import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';
import { apiClient, UserLayoutDto } from '../api/apiClient';
import { stompClient } from '../api/stompClient';

interface ScreenContextType {
  screens: UserLayoutDto[];
  activeScreen: string;
  commandBarPosition: 'TOP' | 'BOTTOM';
  isWsConnected: boolean;
  activeLayoutJson: string | null;
  switchScreen: (screenName: string) => void;
  addScreen: (screenName: string, template?: 'dashboard' | 'blank' | 'clone') => Promise<void>;
  saveActiveLayout: (layoutJson: string) => Promise<void>;
  deleteScreen: (screenName: string) => Promise<void>;
  toggleCommandBarPosition: () => void;
  openBufferRequest: string | null;
  triggerOpenBuffer: (bufferId: string) => void;
  clearOpenBufferRequest: () => void;
}

const ScreenContext = createContext<ScreenContextType | undefined>(undefined);

const DEFAULT_SCREEN_NAME = '01. DASHBOARD';

export const ScreenProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [screens, setScreens] = useState<UserLayoutDto[]>([]);
  const [activeScreen, setActiveScreen] = useState<string>(DEFAULT_SCREEN_NAME);
  const [activeLayoutJson, setActiveLayoutJson] = useState<string | null>(null);
  const [commandBarPosition, setCommandBarPosition] = useState<'TOP' | 'BOTTOM'>('TOP');
  const [isWsConnected, setIsWsConnected] = useState<boolean>(false);
  const [openBufferRequest, setOpenBufferRequest] = useState<string | null>(null);

  // Monitor WebSocket STOMP connection state
  useEffect(() => {
    stompClient.activate();
    const unsub = stompClient.onConnectionChange((connected) => {
      setIsWsConnected(connected);
    });
    return () => {
      unsub();
      stompClient.deactivate();
    };
  }, []);

  // Fetch saved layouts on mount
  useEffect(() => {
    async function loadInitialLayouts() {
      try {
        const fetched = await apiClient.getAllLayouts();
        if (fetched && fetched.length > 0) {
          setScreens(fetched);
          const defaultLayout = fetched.find((s) => s.isDefault) || fetched[0];
          setActiveScreen(defaultLayout.layoutName);
          setActiveLayoutJson(defaultLayout.layoutJson);
          setCommandBarPosition(defaultLayout.commandBarPosition || 'TOP');
        } else {
          // Initialize default dashboard screen
          const initialDefault: UserLayoutDto = {
            layoutName: DEFAULT_SCREEN_NAME,
            layoutJson: '',
            isDefault: true,
            commandBarPosition: 'TOP',
          };
          setScreens([initialDefault]);
          setActiveScreen(DEFAULT_SCREEN_NAME);
        }
      } catch (err) {
        console.warn('[ScreenContext] Backend unreachable, using in-memory default:', err);
        setScreens([
          {
            layoutName: DEFAULT_SCREEN_NAME,
            layoutJson: '',
            isDefault: true,
            commandBarPosition: 'TOP',
          },
        ]);
      }
    }
    loadInitialLayouts();
  }, []);

  // Switch active screen
  const switchScreen = useCallback(
    (screenName: string) => {
      const target = screens.find((s) => s.layoutName === screenName);
      if (target) {
        setActiveScreen(screenName);
        setActiveLayoutJson(target.layoutJson || null);
      }
    },
    [screens]
  );

  // Save current active screen layout to backend
  const saveActiveLayout = useCallback(
    async (layoutJson: string) => {
      setActiveLayoutJson(layoutJson);
      try {
        const payload: UserLayoutDto = {
          layoutName: activeScreen,
          layoutJson,
          isDefault: activeScreen === DEFAULT_SCREEN_NAME,
          commandBarPosition,
        };
        await apiClient.saveLayout(payload);
        setScreens((prev) =>
          prev.map((s) => (s.layoutName === activeScreen ? { ...s, layoutJson } : s))
        );
      } catch (err) {
        console.error('[ScreenContext] Failed to persist layout to backend:', err);
      }
    },
    [activeScreen, commandBarPosition]
  );

  // Add new screen with template options
  const addScreen = useCallback(
    async (screenName: string, template: 'dashboard' | 'blank' | 'clone' = 'blank') => {
      let layoutJsonToUse = '';
      if (template === 'clone' && activeLayoutJson) {
        layoutJsonToUse = activeLayoutJson;
      }

      const newLayout: UserLayoutDto = {
        layoutName: screenName,
        layoutJson: layoutJsonToUse,
        isDefault: false,
        commandBarPosition,
      };

      try {
        await apiClient.saveLayout(newLayout);
      } catch (err) {
        console.warn('[ScreenContext] Could not persist new screen to backend:', err);
      }

      setScreens((prev) => [...prev, newLayout]);
      setActiveScreen(screenName);
      setActiveLayoutJson(layoutJsonToUse);
    },
    [activeLayoutJson, commandBarPosition]
  );

  // Delete a screen
  const deleteScreen = useCallback(
    async (screenName: string) => {
      if (screenName === DEFAULT_SCREEN_NAME) {
        alert('Cannot delete the default dashboard screen.');
        return;
      }
      try {
        await apiClient.deleteLayout(screenName);
      } catch (err) {
        console.warn('[ScreenContext] Delete request failed on server:', err);
      }
      setScreens((prev) => prev.filter((s) => s.layoutName !== screenName));
      if (activeScreen === screenName) {
        setActiveScreen(DEFAULT_SCREEN_NAME);
      }
    },
    [activeScreen]
  );

  // Toggle Command Bar between TOP and BOTTOM
  const toggleCommandBarPosition = useCallback(() => {
    setCommandBarPosition((prev) => {
      const nextPos = prev === 'TOP' ? 'BOTTOM' : 'TOP';
      // Persist preference to current layout
      if (activeLayoutJson) {
        apiClient.saveLayout({
          layoutName: activeScreen,
          layoutJson: activeLayoutJson,
          isDefault: activeScreen === DEFAULT_SCREEN_NAME,
          commandBarPosition: nextPos,
        }).catch(console.error);
      }
      return nextPos;
    });
  }, [activeScreen, activeLayoutJson]);

  const triggerOpenBuffer = useCallback((bufferId: string) => {
    setOpenBufferRequest(bufferId);
  }, []);

  const clearOpenBufferRequest = useCallback(() => {
    setOpenBufferRequest(null);
  }, []);

  return (
    <ScreenContext.Provider
      value={{
        screens,
        activeScreen,
        commandBarPosition,
        isWsConnected,
        activeLayoutJson,
        switchScreen,
        addScreen,
        saveActiveLayout,
        deleteScreen,
        toggleCommandBarPosition,
        openBufferRequest,
        triggerOpenBuffer,
        clearOpenBufferRequest,
      }}
    >
      {children}
    </ScreenContext.Provider>
  );
};

export const useScreens = (): ScreenContextType => {
  const ctx = useContext(ScreenContext);
  if (!ctx) throw new Error('useScreens must be used within a ScreenProvider');
  return ctx;
};
