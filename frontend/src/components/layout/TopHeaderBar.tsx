/**
 * -----------------------------------------------------------------------------
 * TopHeaderBar - APEX Navigation & Screen Control Rail
 * -----------------------------------------------------------------------------
 * Replicates the exact top bar from the Prosperous Universe APEX console:
 * - Left: Hexagonal archive emblem, screen selector dropdown (SCRN: ...),
 *   [SCRN] screen manager, [+ ADD] new screen modal, and [FULL] screen toggle.
 * - Right: System telemetry badges (SQLite vault, port 61069, WS online indicator,
 *   and the user handle 'TUNDER1705' with command bar position settings).
 * -----------------------------------------------------------------------------
 */

import React, { useState } from 'react';
import { useScreens } from '../../context/ScreenContext';
import { Maximize2, Settings, Plus, ChevronDown, Check, Trash2, ArrowUpDown } from 'lucide-react';

export const TopHeaderBar: React.FC = () => {
  const {
    screens,
    activeScreen,
    switchScreen,
    addScreen,
    deleteScreen,
    isWsConnected,
    commandBarPosition,
    toggleCommandBarPosition,
  } = useScreens();

  const [isAddModalOpen, setIsAddModalOpen] = useState(false);
  const [newScreenName, setNewScreenName] = useState('');
  const [selectedTemplate, setSelectedTemplate] = useState<'dashboard' | 'blank' | 'clone'>('blank');
  const [isSettingsOpen, setIsSettingsOpen] = useState(false);
  const [isScreenMenuOpen, setIsScreenMenuOpen] = useState(false);

  // Fullscreen toggle
  const toggleFullScreen = () => {
    if (!document.fullscreenElement) {
      document.documentElement.requestFullscreen().catch(console.error);
    } else {
      document.exitFullscreen().catch(console.error);
    }
  };

  const handleCreateScreen = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newScreenName.trim()) return;
    await addScreen(newScreenName.trim(), selectedTemplate);
    setNewScreenName('');
    setIsAddModalOpen(false);
  };

  return (
    <header className="h-8 bg-[#0e1217] border-b border-[#212832] flex items-center justify-between px-2 text-xs font-mono select-none z-50">
      {/* Left: APEX Emblem + Screen Switcher */}
      <div className="flex items-center space-x-2">
        {/* Hexagonal Archive Logo */}
        <div className="flex items-center space-x-1.5 cursor-pointer mr-1">
          <svg className="w-4 h-4 text-[#f08c00]" viewBox="0 0 100 100" fill="currentColor">
            <polygon points="50,5 95,27.5 95,72.5 50,95 5,72.5 5,27.5" />
          </svg>
          <span className="font-bold text-[#e2e8f0] tracking-wider text-[11px] hidden sm:inline">
            THE ARCHIVE
          </span>
        </div>

        {/* Screen Selector Dropdown */}
        <div className="relative">
          <button
            onClick={() => setIsScreenMenuOpen(!isScreenMenuOpen)}
            className="flex items-center space-x-1.5 bg-[#151b22] hover:bg-[#1a212b] border border-[#212832] hover:border-[#3898ec] px-2 py-0.5 text-[#e2e8f0] text-[11px]"
          >
            <span className="text-[#6b7a8d]">SCRN:</span>
            <span className="font-bold text-[#f08c00]">{activeScreen}</span>
            <ChevronDown className="w-3 h-3 text-[#6b7a8d]" />
          </button>

          {/* Screen Switcher Dropdown Menu */}
          {isScreenMenuOpen && (
            <div className="absolute left-0 top-full mt-1 w-56 bg-[#11161d] border border-[#212832] shadow-2xl py-1 z-50">
              <div className="px-2 py-1 text-[10px] text-[#6b7a8d] uppercase tracking-wider border-b border-[#1f2631]">
                Available Screens
              </div>
              {screens.map((s) => (
                <div
                  key={s.layoutName}
                  className={`flex items-center justify-between px-2 py-1.5 cursor-pointer hover:bg-[#151b22] ${
                    s.layoutName === activeScreen ? 'bg-[#151b22] text-[#3898ec]' : 'text-[#e2e8f0]'
                  }`}
                  onClick={() => {
                    switchScreen(s.layoutName);
                    setIsScreenMenuOpen(false);
                  }}
                >
                  <span className="truncate">{s.layoutName}</span>
                  <div className="flex items-center space-x-1">
                    {s.layoutName === activeScreen && <Check className="w-3 h-3 text-[#3898ec]" />}
                    {s.layoutName !== '01. DASHBOARD' && (
                      <button
                        onClick={(e) => {
                          e.stopPropagation();
                          if (confirm(`Delete screen '${s.layoutName}'?`)) {
                            deleteScreen(s.layoutName);
                          }
                        }}
                        className="text-[#6b7a8d] hover:text-[#e05656] p-0.5"
                      >
                        <Trash2 className="w-3 h-3" />
                      </button>
                    )}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* [+ ADD] Screen Button */}
        <button
          onClick={() => setIsAddModalOpen(true)}
          className="apex-btn-secondary flex items-center space-x-1 hover:text-[#f08c00] hover:border-[#f08c00]"
          title="Create New Screen Layout"
        >
          <Plus className="w-3 h-3 text-[#f08c00]" />
          <span>ADD</span>
        </button>

        {/* [FULL] Fullscreen Toggle */}
        <button
          onClick={toggleFullScreen}
          className="apex-btn-secondary flex items-center space-x-1 hover:text-[#3898ec]"
          title="Toggle Fullscreen Mode"
        >
          <Maximize2 className="w-3 h-3" />
          <span>FULL</span>
        </button>
      </div>

      {/* Right: Telemetry & User Badges */}
      <div className="flex items-center space-x-2 text-[11px]">
        {/* Vault Status Badge */}
        <div className="px-1.5 py-0.5 bg-[#151b22] border border-[#212832] text-[#6b7a8d] hidden md:flex items-center space-x-1">
          <span>VAULT:</span>
          <span className="text-[#e2e8f0] font-semibold">LOCAL (SQLite)</span>
        </div>

        {/* Port Badge */}
        <div className="px-1.5 py-0.5 bg-[#151b22] border border-[#212832] text-[#6b7a8d] hidden lg:block">
          PORT: <span className="text-[#3898ec]">61069</span>
        </div>

        {/* WebSocket Connection Badge (Matching APEX NOTS indicator) */}
        <div
          className={`px-2 py-0.5 border flex items-center space-x-1.5 ${
            isWsConnected
              ? 'border-[#3fb950] text-[#3fb950] bg-[rgba(63,185,80,0.1)]'
              : 'border-[#e05656] text-[#e05656] bg-[rgba(224,86,86,0.1)]'
          }`}
        >
          <span
            className={`w-1.5 h-1.5 rounded-full ${
              isWsConnected ? 'bg-[#3fb950] animate-pulse' : 'bg-[#e05656]'
            }`}
          />
          <span className="font-semibold text-[10px]">
            {isWsConnected ? 'WS: ONLINE' : 'WS: OFFLINE'}
          </span>
        </div>

        {/* User Handle & Settings Menu */}
        <div className="relative">
          <button
            onClick={() => setIsSettingsOpen(!isSettingsOpen)}
            className="flex items-center space-x-1 px-2 py-0.5 bg-[#151b22] hover:bg-[#1a212b] border border-[#212832] text-[#e2e8f0]"
          >
            <span className="font-bold text-[#f08c00]">TUNDER1705</span>
            <Settings className="w-3 h-3 text-[#6b7a8d]" />
          </button>

          {/* Quick Settings Dropdown */}
          {isSettingsOpen && (
            <div className="absolute right-0 top-full mt-1 w-64 bg-[#11161d] border border-[#212832] shadow-2xl p-2 z-50 text-[11px]">
              <div className="text-[10px] text-[#6b7a8d] uppercase tracking-wider mb-2 border-b border-[#212832] pb-1">
                Workspace Preferences
              </div>
              <div className="flex items-center justify-between py-1.5">
                <span className="text-[#e2e8f0]">Command Bar Position:</span>
                <button
                  onClick={toggleCommandBarPosition}
                  className="apex-btn-secondary flex items-center space-x-1 text-[#3898ec] border-[#3898ec]"
                >
                  <ArrowUpDown className="w-3 h-3" />
                  <span>{commandBarPosition}</span>
                </button>
              </div>
              <div className="text-[10px] text-[#6b7a8d] mt-2 border-t border-[#212832] pt-1">
                Backend: Spring Boot 3.3.4 (Java 21)
              </div>
            </div>
          )}
        </div>
      </div>

      {/* [+ ADD SCREEN] APEX Tactical Modal */}
      {isAddModalOpen && (
        <div className="fixed inset-0 bg-black/70 flex items-center justify-center z-50 font-mono">
          <div className="bg-[#11161d] border border-[#3898ec] w-96 p-4 shadow-2xl">
            <div className="flex items-center justify-between border-b border-[#212832] pb-2 mb-3">
              <span className="text-[#f08c00] font-bold text-xs uppercase tracking-wider">
                Create New Screen
              </span>
              <button
                onClick={() => setIsAddModalOpen(false)}
                className="text-[#6b7a8d] hover:text-[#e2e8f0]"
              >
                ✕
              </button>
            </div>

            <form onSubmit={handleCreateScreen} className="space-y-3">
              <div>
                <label className="block text-[#6b7a8d] text-[10px] uppercase mb-1">
                  Screen Identifier / Name:
                </label>
                <input
                  type="text"
                  autoFocus
                  required
                  placeholder="e.g. 02. HARVESTING"
                  value={newScreenName}
                  onChange={(e) => setNewScreenName(e.target.value)}
                  className="w-full bg-[#0a0d11] border border-[#212832] focus:border-[#3898ec] px-2 py-1 text-[#e2e8f0] text-xs outline-none"
                />
              </div>

              <div>
                <label className="block text-[#6b7a8d] text-[10px] uppercase mb-1">
                  Initial Grid Template:
                </label>
                <div className="grid grid-cols-2 gap-2">
                  <button
                    type="button"
                    onClick={() => setSelectedTemplate('blank')}
                    className={`p-2 border text-left text-[11px] ${
                      selectedTemplate === 'blank'
                        ? 'border-[#f08c00] bg-[#1a1f26] text-[#f08c00]'
                        : 'border-[#212832] bg-[#0e1217] text-[#6b7a8d]'
                    }`}
                  >
                    <div className="font-bold">Blank Canvas</div>
                    <div className="text-[9px] text-[#6b7a8d]">Add buffers from sidebar</div>
                  </button>

                  <button
                    type="button"
                    onClick={() => setSelectedTemplate('clone')}
                    className={`p-2 border text-left text-[11px] ${
                      selectedTemplate === 'clone'
                        ? 'border-[#f08c00] bg-[#1a1f26] text-[#f08c00]'
                        : 'border-[#212832] bg-[#0e1217] text-[#6b7a8d]'
                    }`}
                  >
                    <div className="font-bold">Clone Current</div>
                    <div className="text-[9px] text-[#6b7a8d]">Copy active layout</div>
                  </button>
                </div>
              </div>

              <div className="flex justify-end space-x-2 pt-2 border-t border-[#212832]">
                <button
                  type="button"
                  onClick={() => setIsAddModalOpen(false)}
                  className="apex-btn-secondary"
                >
                  Cancel
                </button>
                <button type="submit" className="apex-btn-primary">
                  Create Screen
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </header>
  );
};
