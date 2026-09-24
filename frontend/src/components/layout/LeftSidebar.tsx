/**
 * -----------------------------------------------------------------------------
 * LeftSidebar - APEX Monospace Buffer Rail (SDBR)
 * -----------------------------------------------------------------------------
 * Replicates the vertical quick-launch sidebar seen in Prosperous Universe:
 * - Top group: SCRNS (screens menu), SDBR (sidebar toggle), BFRS (buffer catalog)
 * - Divider line
 * - Buffer launch codes: VLT (vault), SCRP (scraper), TERM (terminal),
 *   LOG (system log), CHAT (chat room), CMDS (command manager).
 * - Bottom: Vertical low-opacity watermark 'THE ARCHIVE' (replicating 'APEX alpha').
 * -----------------------------------------------------------------------------
 */

import React, { useState } from 'react';
import { useScreens } from '../../context/ScreenContext';

interface BufferDefinition {
  id: string;
  code: string;
  name: string;
  description: string;
}

export const AVAILABLE_BUFFERS: BufferDefinition[] = [
  { id: 'media-vault', code: 'VLT', name: 'Media Vault', description: 'Manga, Anime, Music & Game collections' },
  { id: 'scraper-monitor', code: 'SCRP', name: 'Harvester Engine', description: 'Jsoup Scout & Selenium Harvester monitor' },
  { id: 'terminal', code: 'TERM', name: 'XTRM Terminal', description: 'Interactive CLI connected to Spring Boot' },
  { id: 'system-log', code: 'LOG', name: 'System Log', description: 'Live event stream & audit log' },
  { id: 'chat-room', code: 'CHAT', name: 'Workspace Chat', description: 'Real-time team collaboration room' },
];

export const LeftSidebar: React.FC = () => {
  const { triggerOpenBuffer } = useScreens();
  const [isCatalogOpen, setIsCatalogOpen] = useState(false);

  const handleLaunchBuffer = (bufferId: string) => {
    triggerOpenBuffer(bufferId);
    setIsCatalogOpen(false);
  };

  return (
    <aside className="w-12 bg-[#0a0d11] border-r border-[#212832] flex flex-col justify-between items-center py-2 font-mono select-none z-40 relative">
      {/* Top Utility Codes */}
      <div className="flex flex-col items-center space-y-1 w-full">
        {/* BFRS: Buffer Catalog Trigger */}
        <button
          onClick={() => setIsCatalogOpen(!isCatalogOpen)}
          className={`w-10 h-7 flex items-center justify-center text-[10px] font-bold tracking-wider transition-colors ${
            isCatalogOpen
              ? 'bg-[#151b22] text-[#f08c00] border border-[#f08c00]'
              : 'text-[#6b7a8d] hover:text-[#f08c00] hover:bg-[#151b22]'
          }`}
          title="Buffer Catalog (BFRS)"
        >
          BFRS
        </button>

        {/* Separator Line */}
        <div className="w-8 border-b border-[#212832] my-1" />

        {/* Modular Buffer Quick-Launch Codes */}
        {AVAILABLE_BUFFERS.map((buf) => (
          <button
            key={buf.id}
            onClick={() => handleLaunchBuffer(buf.id)}
            className="w-10 h-7 flex items-center justify-center text-[11px] font-bold tracking-wider text-[#8a95a5] hover:text-[#f08c00] hover:bg-[#151b22] transition-colors"
            title={`${buf.name} [${buf.code}] - Click to spawn or focus`}
          >
            {buf.code}
          </button>
        ))}
      </div>

      {/* Buffer Catalog Drawer / Popup */}
      {isCatalogOpen && (
        <div className="absolute left-12 top-2 w-72 bg-[#11161d] border border-[#3898ec] shadow-2xl p-3 z-50 text-xs">
          <div className="flex items-center justify-between border-b border-[#212832] pb-1.5 mb-2">
            <span className="font-bold text-[#f08c00] uppercase text-[11px] tracking-wider">
              Buffer Catalog (BFRS)
            </span>
            <button
              onClick={() => setIsCatalogOpen(false)}
              className="text-[#6b7a8d] hover:text-[#e2e8f0]"
            >
              ✕
            </button>
          </div>

          <p className="text-[10px] text-[#6b7a8d] mb-2">
            Click a buffer to spawn a new panel into the active screen:
          </p>

          <div className="space-y-1.5">
            {AVAILABLE_BUFFERS.map((buf) => (
              <div
                key={buf.id}
                onClick={() => handleLaunchBuffer(buf.id)}
                className="flex items-center justify-between p-1.5 bg-[#151b22] hover:bg-[#1f2631] border border-[#212832] hover:border-[#3898ec] cursor-pointer"
              >
                <div>
                  <div className="font-bold text-[#e2e8f0] flex items-center space-x-1.5">
                    <span className="text-[#3898ec] text-[10px] font-mono">[{buf.code}]</span>
                    <span>{buf.name}</span>
                  </div>
                  <div className="text-[9px] text-[#6b7a8d] truncate">{buf.description}</div>
                </div>
                <span className="text-[10px] text-[#f08c00] font-bold">ADD +</span>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Bottom Vertical Subtle Watermark (Matching 'APEX alpha') */}
      <div className="writing-mode-vertical text-[9px] font-bold text-[#1f2631] tracking-widest uppercase select-none pb-2">
        THE ARCHIVE
      </div>
    </aside>
  );
};
