/**
 * -----------------------------------------------------------------------------
 * TerminalBuffer - Interactive XTRM Terminal (TERM)
 * -----------------------------------------------------------------------------
 * Provides an interactive terminal console directly inside the APEX workspace:
 * - Listens for incoming output streamed from Spring Boot's CommandExecutionService
 *   over WebSocket STOMP (/topic/terminal.output).
 * - Accepts interactive command input, supporting command history (Up/Down arrows),
 *   built-in commands (help, clear), and all APEX commands (SCRP.RUN, MNG.LIST).
 * -----------------------------------------------------------------------------
 */

import React, { useState, useEffect, useRef } from 'react';
import { stompClient } from '../../api/stompClient';
import { apiClient } from '../../api/apiClient';

interface TerminalLine {
  id: string;
  type: 'input' | 'output' | 'error' | 'info';
  text: string;
}

export const TerminalBuffer: React.FC = () => {
  const [lines, setLines] = useState<TerminalLine[]>([
    { id: '1', type: 'info', text: '==================================================' },
    { id: '2', type: 'info', text: '   THE ARCHIVE - APEX COMMAND CONSOLE v1.0        ' },
    { id: '3', type: 'info', text: '   Connected to Spring Boot 3.3.4 (Port 61069)   ' },
    { id: '4', type: 'info', text: '   Type "help" or APEX command (e.g. MNG.LIST)   ' },
    { id: '5', type: 'info', text: '==================================================' },
  ]);
  const [currentInput, setCurrentInput] = useState('');
  const [history, setHistory] = useState<string[]>([]);
  const [historyIdx, setHistoryIdx] = useState(-1);
  const containerRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  // Subscribe to real-time STOMP terminal stream (including Command Bar echo)
  useEffect(() => {
    const sub = stompClient.subscribeToTerminal((payload) => {
      if (typeof payload === 'object' && payload !== null && payload.source === 'BAR') {
        setLines((prev) => [
          ...prev,
          {
            id: `bar-in-${Date.now()}`,
            type: 'input',
            text: `[APEX BAR] > ${payload.command}`,
          },
          {
            id: `bar-out-${Date.now()}`,
            type: 'output',
            text: payload.output,
          },
        ]);
        return;
      }

      const outputText = typeof payload === 'string' ? payload : payload.output || JSON.stringify(payload);
      setLines((prev) => [
        ...prev,
        {
          id: `line-${Date.now()}-${Math.random()}`,
          type: 'output',
          text: outputText,
        },
      ]);
    });

    return () => {
      try {
        sub.unsubscribe();
      } catch {}
    };
  }, []);

  // Auto-scroll to bottom
  useEffect(() => {
    if (containerRef.current) {
      containerRef.current.scrollTop = containerRef.current.scrollHeight;
    }
  }, [lines]);

  const handleKeyDown = async (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter') {
      const trimmed = currentInput.trim();
      if (!trimmed) {
        setLines((prev) => [...prev, { id: `in-${Date.now()}`, type: 'input', text: '' }]);
        return;
      }

      // Add to command history
      setHistory((prev) => [...prev, trimmed]);
      setHistoryIdx(-1);

      // Display user input line
      setLines((prev) => [
        ...prev,
        { id: `in-${Date.now()}`, type: 'input', text: trimmed },
      ]);
      setCurrentInput('');

      // Handle local shell commands
      if (trimmed.toLowerCase() === 'clear') {
        setLines([]);
        return;
      }

      if (trimmed.toLowerCase() === 'help') {
        setLines((prev) => [
          ...prev,
          { id: `h-${Date.now()}`, type: 'info', text: 'Built-in commands: clear, help' },
          { id: `h2-${Date.now()}`, type: 'info', text: 'APEX commands: MNG.LIST, SCRP.RUN, SCRP.SCOUT, SYS.STATUS, WS.CHAT' },
          { id: `h3-${Date.now()}`, type: 'info', text: 'Custom commands: Support Tier 1 (aliases) and Tier 2 (pipelines with &&)' },
        ]);
        return;
      }

      // Dispatch to backend CLI engine
      try {
        const res = await apiClient.executeCommand(trimmed, 'TERM');
        setLines((prev) => [
          ...prev,
          {
            id: `out-${Date.now()}`,
            type: res.success ? 'output' : 'error',
            text: res.output,
          },
        ]);
      } catch (err: any) {
        setLines((prev) => [
          ...prev,
          {
            id: `err-${Date.now()}`,
            type: 'error',
            text: `[ERROR]: ${err.message || 'Execution failed'}`,
          },
        ]);
      }
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      if (history.length === 0) return;
      const nextIdx = historyIdx === -1 ? history.length - 1 : Math.max(0, historyIdx - 1);
      setHistoryIdx(nextIdx);
      setCurrentInput(history[nextIdx] || '');
    } else if (e.key === 'ArrowDown') {
      e.preventDefault();
      if (history.length === 0 || historyIdx === -1) return;
      const nextIdx = historyIdx + 1;
      if (nextIdx >= history.length) {
        setHistoryIdx(-1);
        setCurrentInput('');
      } else {
        setHistoryIdx(nextIdx);
        setCurrentInput(history[nextIdx] || '');
      }
    }
  };

  return (
    <div
      onClick={() => inputRef.current?.focus()}
      className="h-full w-full bg-[#0a0d11] text-[#e2e8f0] p-2 flex flex-col font-mono text-xs overflow-hidden cursor-text select-text"
    >
      <div ref={containerRef} className="flex-1 overflow-auto space-y-0.5 leading-tight">
        {lines.map((l) => (
          <div key={l.id} className="whitespace-pre-wrap break-words">
            {l.type === 'input' && (
              <span className="text-[#f08c00] font-bold select-none">archive@war-room:~$ </span>
            )}
            <span
              className={
                l.type === 'error'
                  ? 'text-[#e05656]'
                  : l.type === 'info'
                  ? 'text-[#3898ec]'
                  : l.type === 'input'
                  ? 'text-[#e2e8f0] font-semibold'
                  : 'text-[#8a95a5]'
              }
            >
              {l.text}
            </span>
          </div>
        ))}

        {/* Active prompt input line */}
        <div className="flex items-center">
          <span className="text-[#f08c00] font-bold select-none mr-1.5">archive@war-room:~$</span>
          <input
            ref={inputRef}
            type="text"
            value={currentInput}
            onChange={(e) => setCurrentInput(e.target.value)}
            onKeyDown={handleKeyDown}
            className="flex-1 bg-transparent border-none outline-none text-[#e2e8f0] font-mono text-xs p-0 m-0"
            autoFocus
          />
        </div>
      </div>
    </div>
  );
};
