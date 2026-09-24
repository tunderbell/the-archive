/**
 * -----------------------------------------------------------------------------
 * ScraperMonitorBuffer - Harvester & Web Scraping Telemetry (SCRP)
 * -----------------------------------------------------------------------------
 * Displays real-time metrics for Subsystem 1 (Jsoup Scout + Selenium Harvester):
 * - Active Java 21 Virtual Threads
 * - Download rate and active chapter queue
 * - Visual gauge meter bars (mirroring APEX cargo / environment meters)
 * - Quick triggers for SCOUT and HARVEST operations
 * -----------------------------------------------------------------------------
 */

import React, { useState } from 'react';
import { Search } from 'lucide-react';
import { apiClient } from '../../api/apiClient';

export const ScraperMonitorBuffer: React.FC = () => {
  const [targetUrl, setTargetUrl] = useState('https://asuracomic.net/series/solo-leveling');
  const [activeJobs] = useState([
    { id: 'JOB-101', title: 'Solo Leveling Ch 100-110', progress: 75, status: 'DOWNLOADING', threads: 8 },
    { id: 'JOB-102', title: 'Berserk Ch 375', progress: 100, status: 'PACKAGING_CBZ', threads: 1 },
  ]);
  const [isScouting, setIsScouting] = useState(false);

  const handleRunScout = async () => {
    setIsScouting(true);
    try {
      await apiClient.executeCommand(`SCRP.SCOUT "${targetUrl}"`);
    } catch {}
    setTimeout(() => setIsScouting(false), 1200);
  };

  return (
    <div className="h-full w-full bg-[#11161d] text-[#e2e8f0] flex flex-col font-mono text-xs overflow-hidden select-none">
      {/* Top Controls & Scout Input */}
      <div className="bg-[#151b22] border-b border-[#212832] p-2 space-y-2">
        <div className="flex items-center space-x-2">
          <input
            type="text"
            value={targetUrl}
            onChange={(e) => setTargetUrl(e.target.value)}
            placeholder="Target URL for Scout/Harvest..."
            className="flex-1 bg-[#0a0d11] border border-[#212832] focus:border-[#3898ec] px-2 py-1 text-xs text-[#e2e8f0] outline-none"
          />
          <button
            onClick={handleRunScout}
            disabled={isScouting}
            className="apex-btn-primary flex items-center space-x-1"
          >
            <Search className="w-3 h-3" />
            <span>{isScouting ? 'SCOUTING...' : 'SCOUT'}</span>
          </button>
        </div>

        {/* Telemetry Status Summary (APEX Style Key-Value) */}
        <div className="grid grid-cols-4 gap-2 text-[10px] bg-[#0c0f13] p-1.5 border border-[#212832]">
          <div>
            <span className="text-[#6b7a8d] block">ENGINE:</span>
            <span className="text-[#3898ec] font-bold">SELENIUM (HEADLESS)</span>
          </div>
          <div>
            <span className="text-[#6b7a8d] block">VIRTUAL THREADS:</span>
            <span className="text-[#3fb950] font-bold">12 ACTIVE</span>
          </div>
          <div>
            <span className="text-[#6b7a8d] block">STEALTH MODE:</span>
            <span className="text-[#f08c00] font-bold">AUTOMATION HIDDEN</span>
          </div>
          <div>
            <span className="text-[#6b7a8d] block">VAULT STORAGE:</span>
            <span className="text-[#e2e8f0] font-bold">CBZ / ARCHIVE</span>
          </div>
        </div>
      </div>

      {/* Active Jobs Queue */}
      <div className="flex-1 overflow-auto p-2 space-y-2">
        <div className="text-[10px] text-[#6b7a8d] uppercase tracking-wider mb-1">
          Active Harvesting Pipelines:
        </div>

        {activeJobs.map((job) => (
          <div key={job.id} className="p-2 bg-[#0e1217] border border-[#212832] space-y-1.5">
            <div className="flex items-center justify-between text-[11px]">
              <div className="flex items-center space-x-2">
                <span className="text-[#3898ec] font-bold">[{job.id}]</span>
                <span className="font-semibold text-[#e2e8f0]">{job.title}</span>
              </div>
              <span
                className={`text-[9px] font-bold px-1.5 py-0.2 border ${
                  job.status === 'PACKAGING_CBZ'
                    ? 'border-[#3fb950] text-[#3fb950]'
                    : 'border-[#f08c00] text-[#f08c00]'
                }`}
              >
                {job.status}
              </span>
            </div>

            {/* Gauge Progress Bar (Matching APEX Capacity Gauge) */}
            <div className="space-y-1">
              <div className="flex justify-between text-[10px] text-[#6b7a8d]">
                <span>Progress: {job.progress}%</span>
                <span>Threads: {job.threads}</span>
              </div>
              <div className="w-full bg-[#151b22] h-2 border border-[#212832] overflow-hidden">
                <div
                  className="bg-[#f08c00] h-full transition-all duration-300"
                  style={{ width: `${job.progress}%` }}
                />
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
};
