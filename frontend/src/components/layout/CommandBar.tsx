/**
 * -----------------------------------------------------------------------------
 * CommandBar - Omnipresent APEX CLI Command Bar
 * -----------------------------------------------------------------------------
 * Provides the keyboard-first command interface for The Archive:
 * - Shortcut activation: Pressing '/' or 'Ctrl+K' focuses the input anywhere.
 * - Position flexibility: Dockable at 'TOP' or 'BOTTOM' based on UserLayout preference.
 * - Autocomplete dropdown: Suggests built-in APEX shorthand commands (SCRP.RUN,
 *   MNG.LIST, SYS.STATUS) and user custom commands.
 * - Execution: Dispatches commands directly to TerminalController via apiClient.
 * -----------------------------------------------------------------------------
 */

import React, { useState, useEffect, useRef } from 'react';
import { useScreens } from '../../context/ScreenContext';
import { apiClient } from '../../api/apiClient';
import { Terminal, Send, CheckCircle2, AlertCircle } from 'lucide-react';

const SUGGESTIONS = [
  { cmd: 'SCRP.RUN', desc: 'Harvest chapters using stealth Selenium & Virtual Threads' },
  { cmd: 'SCRP.SCOUT', desc: 'Fast static HTML scrape using Jsoup' },
  { cmd: 'MNG.LIST', desc: 'List all manga titles registered in local vault' },
  { cmd: 'SYS.STATUS', desc: 'Display JVM, SQLite vault, and thread pool telemetry' },
  { cmd: 'WS.CHAT', desc: 'Broadcast a message to the workspace collaboration room' },
  { cmd: 'CUSTOM.ADD', desc: 'Register a new Tier 1 alias or Tier 2 pipeline' },
];

export const CommandBar: React.FC = () => {
  const { commandBarPosition } = useScreens();
  const [commandInput, setCommandInput] = useState('');
  const [suggestions, setSuggestions] = useState<typeof SUGGESTIONS>([]);
  const [executionResult, setExecutionResult] = useState<{ success: boolean; timeMs: number } | null>(null);
  const [isFocused, setIsFocused] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);

  // Global keyboard shortcut listener ('/' or 'Ctrl+K')
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (
        (e.key === '/' && document.activeElement?.tagName !== 'INPUT' && document.activeElement?.tagName !== 'TEXTAREA') ||
        (e.ctrlKey && e.key === 'k')
      ) {
        e.preventDefault();
        inputRef.current?.focus();
        inputRef.current?.select();
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, []);

  // Filter autocomplete suggestions
  useEffect(() => {
    if (!commandInput.trim()) {
      setSuggestions([]);
      return;
    }
    const query = commandInput.toUpperCase();
    const matched = SUGGESTIONS.filter((s) => s.cmd.startsWith(query) || s.cmd.includes(query));
    setSuggestions(matched);
  }, [commandInput]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const trimmed = commandInput.trim();
    if (!trimmed) return;

    try {
      const res = await apiClient.executeCommand(trimmed);
      setExecutionResult({ success: res.success, timeMs: res.executionTimeMs });
      setCommandInput('');
      setSuggestions([]);
      setTimeout(() => setExecutionResult(null), 4000);
    } catch {
      setExecutionResult({ success: false, timeMs: 0 });
      setTimeout(() => setExecutionResult(null), 4000);
    }
  };

  return (
    <div
      className={`bg-[#0e1217] border-[#212832] font-mono px-3 py-1 flex items-center space-x-2 text-xs select-none z-30 relative ${
        commandBarPosition === 'TOP' ? 'border-b' : 'border-t'
      }`}
    >
      <div className="flex items-center space-x-1.5 text-[#f08c00]">
        <Terminal className="w-3.5 h-3.5" />
        <span className="font-bold text-[11px] tracking-wide">APEX &gt;</span>
      </div>

      <form onSubmit={handleSubmit} className="flex-1 relative">
        <input
          ref={inputRef}
          type="text"
          value={commandInput}
          onChange={(e) => setCommandInput(e.target.value)}
          onFocus={() => setIsFocused(true)}
          onBlur={() => setTimeout(() => setIsFocused(false), 200)}
          placeholder="Enter command or press '/' (e.g. MNG.LIST, SCRP.RUN 'Solo Leveling' 100)..."
          className="w-full bg-[#0a0d11] border border-[#212832] focus:border-[#3898ec] px-2.5 py-1 text-[#e2e8f0] text-xs outline-none placeholder-[#4b5563]"
        />

        {/* Autocomplete Suggestions HUD */}
        {isFocused && suggestions.length > 0 && (
          <div className="absolute left-0 bottom-full mb-1 w-full bg-[#11161d] border border-[#3898ec] shadow-2xl py-1 z-50">
            {suggestions.map((s) => (
              <div
                key={s.cmd}
                onMouseDown={() => {
                  setCommandInput(s.cmd + ' ');
                  inputRef.current?.focus();
                }}
                className="flex items-center justify-between px-3 py-1 hover:bg-[#151b22] cursor-pointer"
              >
                <span className="font-bold text-[#f08c00] text-[11px]">{s.cmd}</span>
                <span className="text-[10px] text-[#6b7a8d]">{s.desc}</span>
              </div>
            ))}
          </div>
        )}
      </form>

      <button
        onClick={handleSubmit}
        className="apex-btn-primary flex items-center space-x-1 py-1 px-2"
        title="Execute Command"
      >
        <Send className="w-3 h-3" />
        <span>RUN</span>
      </button>

      {/* Execution Feedback Indicator */}
      {executionResult && (
        <div
          className={`flex items-center space-x-1 text-[10px] px-2 py-0.5 border ${
            executionResult.success
              ? 'border-[#3fb950] text-[#3fb950] bg-[rgba(63,185,80,0.1)]'
              : 'border-[#e05656] text-[#e05656] bg-[rgba(224,86,86,0.1)]'
          }`}
        >
          {executionResult.success ? (
            <CheckCircle2 className="w-3 h-3 text-[#3fb950]" />
          ) : (
            <AlertCircle className="w-3 h-3 text-[#e05656]" />
          )}
          <span>{executionResult.success ? `SUCCESS (${executionResult.timeMs}ms)` : 'FAILED'}</span>
        </div>
      )}
    </div>
  );
};
