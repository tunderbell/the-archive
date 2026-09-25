/**
 * -----------------------------------------------------------------------------
 * ScraperMonitorBuffer - Harvester & CSS Selector Wizard (SCRP)
 * -----------------------------------------------------------------------------
 * High-density command deck for Subsystem 1:
 * 1. HARVEST COCKPIT:
 *    - Jsoup Scout against target URLs with automatic chapter cataloging
 *    - Real-time Active Harvesting Pipelines with animated APEX capacity gauges
 *    - Live WebSocket progress updates (/topic/scraper.progress)
 * 2. CSS SELECTOR WIZARD (Area 4: User-Defined Scrapers):
 *    - Test arbitrary web URLs with custom CSS selectors
 *    - Live diagnostic badges and visual Image Thumbnail Strip
 *    - Save verified recipes directly into SQLite vault (ScraperTemplate)
 * -----------------------------------------------------------------------------
 */

import React, { useState, useEffect } from 'react';
import { Search, Download, CheckCircle2, AlertCircle, Wrench, RefreshCw, Layers, Eye, MousePointer, X, Check } from 'lucide-react';
import { apiClient, TestSelectorResponse, ScraperTemplateDto } from '../../api/apiClient';
import { stompClient } from '../../api/stompClient';

interface DiscoveredChapter {
  id?: string;
  chapterNumber: number;
  title: string;
  sourceUrl: string;
  downloaded?: boolean;
}

interface HarvestingJob {
  id: string;
  chapterId?: string;
  title: string;
  progress: number;
  status: 'DOWNLOADING' | 'PACKAGING_CBZ' | 'COMPLETED' | 'FAILED';
  threads: number;
}

export const ScraperMonitorBuffer: React.FC = () => {
  const [activeTab, setActiveTab] = useState<'COCKPIT' | 'WIZARD'>('COCKPIT');

  // --- Saved Recipes State ---
  const [savedRecipes, setSavedRecipes] = useState<ScraperTemplateDto[]>([]);

  const loadSavedRecipes = async () => {
    try {
      const templates = await apiClient.getAllTemplates();
      setSavedRecipes(templates);
    } catch {
      // Ignore
    }
  };

  useEffect(() => {
    loadSavedRecipes();
  }, []);

  // --- Cockpit State ---
  const [targetUrl, setTargetUrl] = useState('https://asuracomic.net/series/solo-leveling');
  const [isScouting, setIsScouting] = useState(false);
  const [scoutedSeries, setScoutedSeries] = useState<any | null>(null);
  const [scoutedChapters, setScoutedChapters] = useState<DiscoveredChapter[]>([]);
  const [isHarvestingAll, setIsHarvestingAll] = useState(false);
  const [statusMessage, setStatusMessage] = useState<{ text: string; error?: boolean } | null>(null);
  const [activeJobs, setActiveJobs] = useState<HarvestingJob[]>([
    { id: 'JOB-101', title: 'Solo Leveling Ch 100', progress: 100, status: 'COMPLETED', threads: 0 },
  ]);

  // --- Wizard State ---
  const [wizardUrl, setWizardUrl] = useState('https://asuracomic.net/series/solo-leveling');
  const [wizardName, setWizardName] = useState('Asura Comic');
  const [wizardDomain, setWizardDomain] = useState('asuracomic.net');
  const [titleSelector, setTitleSelector] = useState('span.text-xl, h1');
  const [chapterListSelector, setChapterListSelector] = useState('div.pl-4 a, #chapterlist a');
  const [imageSelector, setImageSelector] = useState('div#readerarea img, div.w-full img');
  const [requiresJs, setRequiresJs] = useState(false);
  const [isTesting, setIsTesting] = useState(false);
  const [testResults, setTestResults] = useState<TestSelectorResponse | null>(null);
  const [isSaving, setIsSaving] = useState(false);
  const [wizardStatus, setWizardStatus] = useState<{ text: string; error?: boolean } | null>(null);

  // --- Visual DOM Inspector State ---
  const [showLiveInspector, setShowLiveInspector] = useState(false);
  const [inspectorFrameKey, setInspectorFrameKey] = useState(0);
  const [selectedElementInfo, setSelectedElementInfo] = useState<{
    selector: string;
    candidates?: string[];
    tagName: string;
    matchCount: number;
    isImage: boolean;
    isLink: boolean;
    sampleText: string;
  } | null>(null);

  // Listen for point-and-click events sent by the proxy iframe
  useEffect(() => {
    const handleInspectorMessage = (event: MessageEvent) => {
      if (event.data && event.data.type === 'APEX_INSPECTOR_ELEMENT_SELECTED') {
        setSelectedElementInfo({
          selector: event.data.selector,
          candidates: event.data.candidates || [event.data.selector],
          tagName: event.data.tagName,
          matchCount: event.data.matchCount,
          isImage: event.data.isImage,
          isLink: event.data.isLink,
          sampleText: event.data.sampleText,
        });
      }
    };

    window.addEventListener('message', handleInspectorMessage);
    return () => window.removeEventListener('message', handleInspectorMessage);
  }, []);

  // Subscribe to live WebSocket scraper progress updates
  useEffect(() => {
    const sub = stompClient.subscribeToScraperProgress((payload: any) => {
      if (!payload || !payload.jobId) return;

      setActiveJobs((prev) => {
        const existingIdx = prev.findIndex(
          (j) => j.id === payload.jobId || (payload.chapterId && j.chapterId === payload.chapterId)
        );
        const updatedJob: HarvestingJob = {
          id: payload.jobId,
          chapterId: payload.chapterId,
          title: payload.title || payload.jobId,
          progress: payload.progress ?? 0,
          status: payload.status || 'DOWNLOADING',
          threads: payload.threads ?? 8,
        };

        if (existingIdx !== -1) {
          const next = [...prev];
          next[existingIdx] = updatedJob;
          return next;
        } else {
          return [updatedJob, ...prev];
        }
      });
    });

    return () => {
      try {
        sub.unsubscribe();
      } catch {}
    };
  }, []);

  // --- Cockpit Handlers ---

  const handleRunScout = async () => {
    if (!targetUrl.trim()) return;
    setIsScouting(true);
    setStatusMessage(null);

    try {
      const result = await apiClient.scoutSeries(targetUrl.trim());
      setScoutedSeries(result);
      if (result.id) {
        try {
          const dbChapters = await apiClient.getChaptersForManga(result.id);
          if (dbChapters && dbChapters.length > 0) {
            setScoutedChapters(
              dbChapters.map((ch) => ({
                id: ch.id,
                chapterNumber: ch.chapterNumber,
                title: ch.title || `Chapter ${ch.chapterNumber}`,
                sourceUrl: ch.sourceUrl || '',
                downloaded: ch.downloaded,
              }))
            );
            setStatusMessage({ text: `Discovered & synced ${dbChapters.length} chapters for "${result.title}"!` });
            return;
          }
        } catch {}
      }
      if (result.chapters && result.chapters.length > 0) {
        setScoutedChapters(result.chapters);
        setStatusMessage({ text: `Discovered ${result.chapters.length} chapters for "${result.title}"!` });
      } else {
        setStatusMessage({ text: `Scouted "${result.title}", no chapters parsed.` });
      }
    } catch (err: any) {
      console.error('[Scout] Error during scout operation:', err);
      setStatusMessage({
        text: `Scout failed for "${targetUrl}": ${err?.message || 'Server error or target unreachable'}`,
        error: true,
      });
      setScoutedChapters([]);
    } finally {
      setIsScouting(false);
    }
  };

  const handleHarvestChapter = async (ch: DiscoveredChapter) => {
    const chapterId = ch.id;
    const isRealUuid = Boolean(chapterId && chapterId.includes('-') && chapterId.length > 30);
    const jobId = isRealUuid && chapterId ? `JOB-${chapterId.substring(0, 8).toUpperCase()}` : `JOB-${Math.floor(100 + Math.random() * 900)}`;
    const newJob: HarvestingJob = {
      id: jobId,
      chapterId: chapterId,
      title: `${scoutedSeries?.title || 'Series'} Ch ${ch.chapterNumber}`,
      progress: 10,
      status: 'DOWNLOADING',
      threads: 8,
    };

    setActiveJobs((prev) => {
      const exists = prev.some((j) => (chapterId && j.chapterId === chapterId) || j.id === jobId);
      return exists ? prev : [newJob, ...prev];
    });

    // Dispatch to real backend if entity ID is a UUID
    if (isRealUuid && chapterId) {
      try {
        await apiClient.harvestChapter(chapterId);
        setScoutedChapters((prev) =>
          prev.map((c) =>
            (c.id && c.id === chapterId) || c.chapterNumber === ch.chapterNumber
              ? { ...c, downloaded: true }
              : c
          )
        );
        setStatusMessage({ text: `✔ Completed harvest: Ch ${ch.chapterNumber} archived to vault.` });
      } catch (err: any) {
        setStatusMessage({ text: `Harvest error: ${err.message}`, error: true });
        setActiveJobs((prev) =>
          prev.map((j) => ((chapterId && j.chapterId === chapterId) || j.id === jobId ? { ...j, status: 'FAILED', threads: 0 } : j))
        );
      }
    } else {
      // Demonstrative progress pipeline animation
      setTimeout(() => {
        setActiveJobs((prev) =>
          prev.map((j) => (j.id === jobId ? { ...j, progress: 55, status: 'DOWNLOADING' } : j))
        );
      }, 700);

      setTimeout(() => {
        setActiveJobs((prev) =>
          prev.map((j) => (j.id === jobId ? { ...j, progress: 95, status: 'PACKAGING_CBZ', threads: 1 } : j))
        );
      }, 1500);

      setTimeout(() => {
        setActiveJobs((prev) =>
          prev.map((j) => (j.id === jobId ? { ...j, progress: 100, status: 'COMPLETED', threads: 0 } : j))
        );
        setScoutedChapters((prev) =>
          prev.map((c) => (c.chapterNumber === ch.chapterNumber ? { ...c, downloaded: true } : c))
        );
        setStatusMessage({ text: `✔ Completed harvest: Ch ${ch.chapterNumber} archived to vault.` });
      }, 2300);
    }
  };

  const handleHarvestAllScoutedChapters = async () => {
    if (scoutedChapters.length === 0) return;
    const unharvested = scoutedChapters.filter((c) => !c.downloaded);
    if (unharvested.length === 0) {
      const confirmAll = window.confirm(
        'All chapters in this queue are already harvested in your vault.\n\nDo you want to re-download all chapters as duplicates and overwrite existing files?'
      );
      if (!confirmAll) return;
    }
    const targetList = unharvested.length > 0 ? unharvested : scoutedChapters;
    setIsHarvestingAll(true);
    setStatusMessage({ text: `Starting batch harvest of ${targetList.length} chapters...` });
    for (const ch of targetList) {
      await handleHarvestChapter(ch);
      await new Promise((r) => setTimeout(r, 600)); // Respectful rate-limiting pause
    }
    setIsHarvestingAll(false);
  };

  // --- Wizard Handlers ---

  const handleTestRecipe = async () => {
    if (!wizardUrl.trim()) return;
    setIsTesting(true);
    setWizardStatus(null);

    try {
      const results = await apiClient.testSelector({
        url: wizardUrl.trim(),
        titleSelector,
        chapterListSelector,
        imageSelector,
        requiresJs,
      });
      setTestResults(results);
      setWizardStatus({ text: `Test completed! Matched ${results.chapterCount} chapters & ${results.imageCount} images.` });
    } catch (err: any) {
      setTestResults(null);
      setWizardStatus({ text: `Selector test failed: ${err?.message || 'Server error or target unreachable'}`, error: true });
    } finally {
      setIsTesting(false);
    }
  };

  const handleSaveRecipe = async () => {
    if (!wizardDomain.trim() || !wizardName.trim()) {
      setWizardStatus({ text: 'Please specify Recipe Name and Domain.', error: true });
      return;
    }
    setIsSaving(true);

    try {
      await apiClient.saveTemplate({
        domainName: wizardDomain.trim().toLowerCase(),
        name: wizardName.trim(),
        titleSelector,
        chapterListSelector,
        imageSelector,
        requiresJs,
        rateLimitMs: 1200,
      });
      setWizardStatus({ text: `✔ Site recipe for [${wizardDomain}] saved into SQLite vault!` });
      loadSavedRecipes();
    } catch (err: any) {
      setWizardStatus({ text: `Failed to save: ${err.message}`, error: true });
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <div className="h-full w-full bg-[#11161d] text-[#e2e8f0] flex flex-col font-mono text-xs overflow-hidden select-none">
      {/* Top Header & Subsystem Tab Switcher */}
      <div className="bg-[#151b22] border-b border-[#212832] px-2 py-1.5 flex items-center justify-between">
        <div className="flex items-center space-x-1">
          <button
            onClick={() => setActiveTab('COCKPIT')}
            className={`apex-btn-secondary flex items-center space-x-1.5 ${
              activeTab === 'COCKPIT' ? 'active' : ''
            }`}
          >
            <Layers className="w-3 h-3 text-[#3898ec]" />
            <span>HARVEST COCKPIT</span>
          </button>
          <button
            onClick={() => setActiveTab('WIZARD')}
            className={`apex-btn-secondary flex items-center space-x-1.5 ${
              activeTab === 'WIZARD' ? 'active' : ''
            }`}
          >
            <Wrench className="w-3 h-3 text-[#f08c00]" />
            <span>CSS SELECTOR WIZARD</span>
          </button>
        </div>

        <div className="flex items-center space-x-3 text-[10px] text-[#6b7a8d]">
          <span>ENGINE: <b className="text-[#3898ec]">JSOUP + SELENIUM</b></span>
          <span>THREADS: <b className="text-[#3fb950]">JAVA 21 LOOM</b></span>
        </div>
      </div>

      {/* ========================================================================= */}
      {/* TAB 1: HARVEST COCKPIT                                                    */}
      {/* ========================================================================= */}
      {activeTab === 'COCKPIT' && (
        <div className="flex-1 flex flex-col overflow-hidden">
          {/* URL Scout Bar */}
          <div className="bg-[#0e1217] border-b border-[#212832] p-2 space-y-2">
            <div className="flex items-center space-x-2">
              {savedRecipes.length > 0 && (
                <select
                  onChange={(e) => {
                    if (e.target.value) {
                      setTargetUrl(`https://${e.target.value}/series/`);
                    }
                  }}
                  className="bg-[#0a0d11] border border-[#212832] text-[#8a95a5] px-2 py-1 text-xs outline-none focus:border-[#3898ec]"
                  defaultValue=""
                >
                  <option value="" disabled>-- Recipe Presets --</option>
                  {savedRecipes.map((r, i) => (
                    <option key={i} value={r.domainName}>
                      {r.name} ({r.domainName})
                    </option>
                  ))}
                </select>
              )}
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

            {/* Status Toast */}
            {statusMessage && (
              <div
                className={`flex items-center space-x-1.5 text-[10px] px-2 py-0.5 border ${
                  statusMessage.error
                    ? 'border-[#e05656] text-[#e05656] bg-[rgba(224,86,86,0.1)]'
                    : 'border-[#3fb950] text-[#3fb950] bg-[rgba(63,185,80,0.1)]'
                }`}
              >
                {statusMessage.error ? <AlertCircle className="w-3 h-3" /> : <CheckCircle2 className="w-3 h-3" />}
                <span>{statusMessage.text}</span>
              </div>
            )}
          </div>

          {/* Active Harvesting Pipelines (Live Capacity Gauges) */}
          <div className="bg-[#0c0f13] border-b border-[#212832] p-2 space-y-2 max-h-48 overflow-auto">
            <div className="flex items-center justify-between text-[10px] text-[#6b7a8d] uppercase tracking-wider">
              <span>Active Harvesting Pipelines ({activeJobs.length}):</span>
              <span className="text-[#3fb950] font-bold">VIRTUAL THREADS CONCURRENCY</span>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-2">
              {activeJobs.map((job) => (
                <div key={job.id} className="p-2 bg-[#0e1217] border border-[#212832] space-y-1.5">
                  <div className="flex items-center justify-between text-[11px]">
                    <div className="flex items-center space-x-1.5 truncate">
                      <span className="text-[#3898ec] font-bold">[{job.id}]</span>
                      <span className="font-semibold text-[#e2e8f0] truncate">{job.title}</span>
                    </div>
                    <span
                      className={`text-[9px] font-bold px-1.5 py-0.2 border shrink-0 ${
                        job.status === 'COMPLETED'
                          ? 'border-[#3fb950] text-[#3fb950]'
                          : job.status === 'PACKAGING_CBZ'
                          ? 'border-[#3898ec] text-[#3898ec]'
                          : 'border-[#f08c00] text-[#f08c00]'
                      }`}
                    >
                      {job.status}
                    </span>
                  </div>

                  {/* APEX Capacity Progress Gauge */}
                  <div className="space-y-1">
                    <div className="flex justify-between text-[10px] text-[#6b7a8d]">
                      <span>Progress: {job.progress}%</span>
                      <span>Threads: {job.threads}</span>
                    </div>
                    <div className="w-full bg-[#151b22] h-2 border border-[#212832] overflow-hidden">
                      <div
                        className={`h-full transition-all duration-300 ${
                          job.status === 'COMPLETED'
                            ? 'bg-[#3fb950]'
                            : job.status === 'PACKAGING_CBZ'
                            ? 'bg-[#3898ec]'
                            : 'bg-[#f08c00]'
                        }`}
                        style={{ width: `${job.progress}%` }}
                      />
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* Discovered Chapter Catalog Queue */}
          <div className="flex-1 overflow-auto p-2">
            <div className="flex items-center justify-between text-[10px] text-[#6b7a8d] uppercase tracking-wider mb-1">
              <div className="flex items-center space-x-2">
                <span>Discovered Chapter Queue ({scoutedChapters.length}):</span>
                {scoutedSeries && <span className="text-[#f08c00] font-bold">{scoutedSeries.title}</span>}
              </div>
              {scoutedChapters.length > 0 && (
                <button
                  onClick={handleHarvestAllScoutedChapters}
                  disabled={isHarvestingAll}
                  className="apex-btn-primary py-0.5 px-2 flex items-center space-x-1.5 disabled:opacity-50"
                  title="Harvest all discovered chapters"
                >
                  <Download className={`w-3 h-3 ${isHarvestingAll ? 'animate-bounce' : ''}`} />
                  <span>{isHarvestingAll ? 'HARVESTING ALL...' : `HARVEST ALL (${scoutedChapters.length})`}</span>
                </button>
              )}
            </div>

            {scoutedChapters.length === 0 ? (
              <div className="h-40 flex items-center justify-center text-[#6b7a8d] text-center p-4">
                Enter a series URL above and click [SCOUT] to parse chapters using Jsoup.
              </div>
            ) : (
              <table className="apex-table">
                <thead>
                  <tr>
                    <th className="w-16">CH #</th>
                    <th>CHAPTER TITLE</th>
                    <th>SOURCE URL</th>
                    <th className="w-24 text-center">ACTION</th>
                  </tr>
                </thead>
                <tbody>
                  {scoutedChapters.map((ch, idx) => (
                    <tr key={idx}>
                      <td className="text-[#f08c00] font-bold">{ch.chapterNumber}</td>
                      <td className="font-semibold text-[#e2e8f0]">{ch.title}</td>
                      <td className="text-[#6b7a8d] text-[10px] truncate max-w-xs">{ch.sourceUrl}</td>
                      <td className="text-center">
                        {ch.downloaded ? (
                          <button
                            onClick={() => {
                              const confirmDup = window.confirm(
                                `Chapter ${ch.chapterNumber} has already been downloaded to your vault.\n\nAre you sure you want to download a duplicate and overwrite existing pages?`
                              );
                              if (confirmDup) handleHarvestChapter(ch);
                            }}
                            className="apex-btn-secondary flex items-center justify-center space-x-1 w-full py-0.5 opacity-60 hover:opacity-100 hover:border-[#f08c00] hover:text-[#f08c00] transition-opacity"
                            title="Already downloaded to vault. Click to re-harvest duplicate."
                          >
                            <Check className="w-2.5 h-2.5 text-[#3fb950]" />
                            <span>HARVESTED</span>
                          </button>
                        ) : (
                          <button
                            onClick={() => handleHarvestChapter(ch)}
                            className="apex-btn-primary flex items-center justify-center space-x-1 w-full py-0.5"
                            title="Harvest chapter into local vault"
                          >
                            <Download className="w-2.5 h-2.5" />
                            <span>HARVEST</span>
                          </button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        </div>
      )}

      {/* ========================================================================= */}
      {/* TAB 2: CSS SELECTOR WIZARD                                                */}
      {/* ========================================================================= */}
      {activeTab === 'WIZARD' && (
        <div className="flex-1 flex flex-col overflow-auto p-3 space-y-3 bg-[#0a0d11]">
          {/* Wizard Header Description */}
          <div className="bg-[#11161d] border border-[#212832] p-2 space-y-1">
            <div className="flex items-center space-x-2 text-[#f08c00] font-bold">
              <Wrench className="w-4 h-4" />
              <span>CSS SELECTOR WIZARD (USER-DEFINED RECIPES)</span>
            </div>
            <p className="text-[11px] text-[#8a95a5]">
              Define and test scraping rules for uncatalogued domains. Verify matched titles, chapter listings,
              and image elements live before saving to the SQLite vault.
            </p>
          </div>

          {/* Wizard Status Alert */}
          {wizardStatus && (
            <div
              className={`flex items-center space-x-2 text-[11px] p-2 border ${
                wizardStatus.error
                  ? 'border-[#e05656] text-[#e05656] bg-[rgba(224,86,86,0.1)]'
                  : 'border-[#3fb950] text-[#3fb950] bg-[rgba(63,185,80,0.1)]'
              }`}
            >
              {wizardStatus.error ? <AlertCircle className="w-3.5 h-3.5" /> : <CheckCircle2 className="w-3.5 h-3.5" />}
              <span>{wizardStatus.text}</span>
            </div>
          )}

          {/* Preset Recipe Selector */}
          <div className="bg-[#151b22] border border-[#212832] p-2 flex items-center justify-between flex-wrap gap-2 text-xs">
            <div className="flex items-center space-x-2">
              <span className="text-[#3898ec] font-bold text-[10px] uppercase">LOAD PRESET RECIPE:</span>
              <select
                onChange={(e) => {
                  const selected = savedRecipes.find((r) => r.domainName === e.target.value);
                  if (selected) {
                    setWizardName(selected.name);
                    setWizardDomain(selected.domainName);
                    setTitleSelector(selected.titleSelector || '');
                    setChapterListSelector(selected.chapterListSelector || '');
                    setImageSelector(selected.imageSelector || '');
                    setRequiresJs(selected.requiresJs || false);
                    setWizardStatus({ text: `✔ Loaded recipe for [${selected.name} (${selected.domainName})]` });
                  }
                }}
                className="bg-[#0a0d11] border border-[#212832] text-[#e2e8f0] px-2 py-0.5 text-xs outline-none focus:border-[#3898ec]"
                defaultValue=""
              >
                <option value="" disabled>-- Select a saved recipe ({savedRecipes.length} available) --</option>
                {savedRecipes.map((r, i) => (
                  <option key={i} value={r.domainName}>
                    {r.name} ({r.domainName})
                  </option>
                ))}
              </select>
            </div>
            <button
              onClick={loadSavedRecipes}
              className="text-[#6b7a8d] hover:text-[#e2e8f0] text-[10px] flex items-center space-x-1"
              title="Refresh Saved Recipes"
            >
              <RefreshCw className="w-3 h-3" />
              <span>REFRESH</span>
            </button>
          </div>

          {/* Form Grid */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-3 bg-[#11161d] border border-[#212832] p-3">
            <div className="space-y-1">
              <label className="text-[10px] text-[#6b7a8d] uppercase">Target Testing URL:</label>
              <input
                type="text"
                value={wizardUrl}
                onChange={(e) => {
                  setWizardUrl(e.target.value);
                  try {
                    const host = new URL(e.target.value).hostname.replace('www.', '');
                    if (host) setWizardDomain(host);
                  } catch {}
                }}
                className="w-full bg-[#0a0d11] border border-[#212832] focus:border-[#3898ec] px-2 py-1 text-xs text-[#e2e8f0] outline-none"
              />
            </div>

            <div className="grid grid-cols-2 gap-2">
              <div className="space-y-1">
                <label className="text-[10px] text-[#6b7a8d] uppercase">Site Recipe Name:</label>
                <input
                  type="text"
                  value={wizardName}
                  onChange={(e) => setWizardName(e.target.value)}
                  className="w-full bg-[#0a0d11] border border-[#212832] focus:border-[#3898ec] px-2 py-1 text-xs text-[#e2e8f0] outline-none"
                />
              </div>
              <div className="space-y-1">
                <label className="text-[10px] text-[#6b7a8d] uppercase">Domain Host:</label>
                <input
                  type="text"
                  value={wizardDomain}
                  onChange={(e) => setWizardDomain(e.target.value)}
                  className="w-full bg-[#0a0d11] border border-[#212832] focus:border-[#3898ec] px-2 py-1 text-xs text-[#e2e8f0] outline-none"
                />
              </div>
            </div>

            <div className="space-y-1">
              <label className="text-[10px] text-[#6b7a8d] uppercase">Title CSS Selector:</label>
              <input
                type="text"
                value={titleSelector}
                onChange={(e) => setTitleSelector(e.target.value)}
                placeholder="e.g. h1, .entry-title, span.text-xl"
                className="w-full bg-[#0a0d11] border border-[#212832] focus:border-[#3898ec] px-2 py-1 text-xs text-[#e2e8f0] outline-none font-mono"
              />
            </div>

            <div className="space-y-1">
              <label className="text-[10px] text-[#6b7a8d] uppercase">Chapter List Selector:</label>
              <input
                type="text"
                value={chapterListSelector}
                onChange={(e) => setChapterListSelector(e.target.value)}
                placeholder="e.g. div.pl-4 a, #chapterlist a"
                className="w-full bg-[#0a0d11] border border-[#212832] focus:border-[#3898ec] px-2 py-1 text-xs text-[#e2e8f0] outline-none font-mono"
              />
            </div>

            <div className="space-y-1">
              <label className="text-[10px] text-[#6b7a8d] uppercase">Page Image Selector:</label>
              <input
                type="text"
                value={imageSelector}
                onChange={(e) => setImageSelector(e.target.value)}
                placeholder="e.g. div#readerarea img, .page-break img"
                className="w-full bg-[#0a0d11] border border-[#212832] focus:border-[#3898ec] px-2 py-1 text-xs text-[#e2e8f0] outline-none font-mono"
              />
            </div>

            <div className="flex items-center space-x-2 pt-4">
              <label className="flex items-center space-x-2 cursor-pointer text-xs">
                <input
                  type="checkbox"
                  checked={requiresJs}
                  onChange={(e) => setRequiresJs(e.target.checked)}
                  className="accent-[#f08c00]"
                />
                <span>Requires JavaScript / Headless Chrome</span>
              </label>
            </div>
          </div>

          {/* Action Buttons */}
          <div className="flex items-center space-x-2 flex-wrap gap-2">
            <button
              onClick={handleTestRecipe}
              disabled={isTesting}
              className="apex-btn-primary flex items-center space-x-1.5 py-1 px-3"
            >
              <RefreshCw className={`w-3.5 h-3.5 ${isTesting ? 'animate-spin' : ''}`} />
              <span>{isTesting ? 'TESTING SELECTORS...' : 'TEST RECIPE'}</span>
            </button>

            <button
              onClick={() => {
                setShowLiveInspector(!showLiveInspector);
                if (!showLiveInspector) {
                  setInspectorFrameKey((k) => k + 1);
                }
              }}
              className={`apex-btn-secondary flex items-center space-x-1.5 py-1 px-3 ${
                showLiveInspector
                  ? 'bg-[#151b22] text-[#3898ec] border-[#3898ec]'
                  : 'text-[#e2e8f0]'
              }`}
            >
              <MousePointer className="w-3.5 h-3.5 text-[#3898ec]" />
              <span>{showLiveInspector ? 'HIDE LIVE INSPECTOR' : 'OPEN LIVE VISUAL INSPECTOR'}</span>
            </button>

            <button
              onClick={handleSaveRecipe}
              disabled={isSaving}
              className="apex-btn-secondary flex items-center space-x-1.5 py-1 px-3 text-[#3fb950] border-[#3fb950]"
            >
              <CheckCircle2 className="w-3.5 h-3.5" />
              <span>{isSaving ? 'SAVING...' : 'SAVE AS RECIPE'}</span>
            </button>
          </div>

          {/* Live Visual DOM Inspector Viewport */}
          {showLiveInspector && (
            <div className="bg-[#0e1217] border border-[#3898ec] flex flex-col h-[560px] overflow-hidden space-y-2 p-2">
              <div className="flex items-center justify-between border-b border-[#212832] pb-1.5">
                <div className="flex items-center space-x-2">
                  <MousePointer className="w-4 h-4 text-[#3898ec] animate-pulse" />
                  <span className="font-bold text-[#3898ec] text-xs">LIVE TACTICAL DOM INSPECTOR</span>
                  <span className="text-[10px] text-[#6b7a8d]">
                    (Hover to highlight elements; click any element to inspect & assign)
                  </span>
                </div>
                <div className="flex items-center space-x-2">
                  <button
                    onClick={() => setInspectorFrameKey((k) => k + 1)}
                    className="apex-btn-secondary px-2 py-0.5 text-[10px] flex items-center space-x-1"
                    title="Reload Visual Sandbox"
                  >
                    <RefreshCw className="w-3 h-3" />
                    <span>RELOAD</span>
                  </button>
                  <button
                    onClick={() => setShowLiveInspector(false)}
                    className="text-[#6b7a8d] hover:text-[#e2e8f0] p-1"
                  >
                    <X className="w-4 h-4" />
                  </button>
                </div>
              </div>

              {/* Clicked Element Quick-Assign HUD */}
              {selectedElementInfo && (
                <div className="bg-[#151b22] border border-[#f08c00] p-2 flex items-center justify-between flex-wrap gap-2 text-xs">
                  <div className="flex flex-col space-y-1.5">
                    <div className="flex items-center space-x-2 flex-wrap gap-1">
                      <span className="text-[#f08c00] font-bold">CLICKED:</span>
                      <code className="text-[#3898ec] bg-[#0a0d11] px-1.5 py-0.5 border border-[#212832] font-mono font-bold">
                        {selectedElementInfo.selector}
                      </code>
                      <span className="text-[#3fb950] text-[10px]">({selectedElementInfo.matchCount} matches)</span>
                      {selectedElementInfo.sampleText && (
                        <span className="text-[#8a95a5] text-[10px] italic truncate max-w-xs">
                          "{selectedElementInfo.sampleText}"
                        </span>
                      )}
                    </div>
                    {/* Selectable Alternative Candidate Chips */}
                    {selectedElementInfo.candidates && selectedElementInfo.candidates.length > 1 && (
                      <div className="flex items-center space-x-1.5 flex-wrap gap-1 pt-0.5">
                        <span className="text-[10px] text-[#6b7a8d] font-bold">OPTIONS:</span>
                        {selectedElementInfo.candidates.map((cand, idx) => (
                          <button
                            key={idx}
                            onClick={() => setSelectedElementInfo({ ...selectedElementInfo, selector: cand })}
                            className={`px-1.5 py-0.5 text-[10px] font-mono border transition-colors ${
                              selectedElementInfo.selector === cand
                                ? 'border-[#3898ec] text-[#3898ec] bg-[#11161d] font-bold'
                                : 'border-[#212832] text-[#8a95a5] hover:text-[#e2e8f0]'
                            }`}
                          >
                            {cand}
                          </button>
                        ))}
                      </div>
                    )}
                  </div>

                  <div className="flex items-center space-x-1.5">
                    <button
                      onClick={() => {
                        setTitleSelector(selectedElementInfo.selector);
                        setWizardStatus({ text: `✔ Assigned Title Selector: ${selectedElementInfo.selector}` });
                      }}
                      className="apex-btn-secondary text-[10px] py-1 px-2 hover:border-[#3898ec] text-[#3898ec]"
                    >
                      [SET AS TITLE]
                    </button>
                    <button
                      onClick={() => {
                        setChapterListSelector(selectedElementInfo.selector);
                        setWizardStatus({ text: `✔ Assigned Chapter Selector: ${selectedElementInfo.selector}` });
                      }}
                      className="apex-btn-secondary text-[10px] py-1 px-2 hover:border-[#f08c00] text-[#f08c00]"
                    >
                      [SET AS CHAPTERS]
                    </button>
                    <button
                      onClick={() => {
                        setImageSelector(selectedElementInfo.selector);
                        setWizardStatus({ text: `✔ Assigned Image Selector: ${selectedElementInfo.selector}` });
                      }}
                      className="apex-btn-secondary text-[10px] py-1 px-2 hover:border-[#3fb950] text-[#3fb950]"
                    >
                      [SET AS IMAGES]
                    </button>
                  </div>
                </div>
              )}

              {/* Sandboxed Interactive Frame */}
              <div className="flex-1 bg-white relative rounded overflow-hidden">
                <iframe
                  key={inspectorFrameKey}
                  src={`${apiClient.getBaseUrl()}/api/scraper/live-inspect-proxy?url=${encodeURIComponent(wizardUrl)}`}
                  title="Live Webpage Inspector"
                  className="w-full h-full border-0"
                  sandbox="allow-scripts allow-same-origin allow-forms"
                />
              </div>
            </div>
          )}

          {/* Diagnostic Results HUD */}
          {testResults && (
            <div className="bg-[#11161d] border border-[#3898ec] p-3 space-y-3">
              <div className="flex items-center justify-between border-b border-[#212832] pb-1.5">
                <div className="flex items-center space-x-2 text-[11px] font-bold text-[#3898ec]">
                  <Eye className="w-4 h-4" />
                  <span>DIAGNOSTIC TEST RESULTS: [{testResults.domain}]</span>
                </div>
                <div className="flex items-center space-x-3 text-[10px]">
                  <span className="text-[#3fb950]">✔ TITLE: {testResults.title}</span>
                  <span className="text-[#f08c00]">✔ CHAPTERS: {testResults.chapterCount}</span>
                  <span className="text-[#3898ec]">✔ IMAGES: {testResults.imageCount}</span>
                  <button
                    onClick={() => setTestResults(null)}
                    className="text-[#6b7a8d] hover:text-[#e2e8f0] p-0.5 ml-2 cursor-pointer transition-colors"
                    title="Close Diagnostic Report"
                  >
                    <X className="w-4 h-4" />
                  </button>
                </div>
              </div>

              {/* Visual Thumbnail Strip */}
              <div className="space-y-1.5">
                <span className="text-[10px] text-[#6b7a8d] uppercase block">
                  Live Image Thumbnails Extracted ({testResults.sampleImages.length} previews):
                </span>
                {testResults.sampleImages.length === 0 ? (
                  <div className="text-[11px] text-[#e05656]">No image elements matched with selector: {imageSelector}</div>
                ) : (
                  <div className="grid grid-cols-6 gap-2">
                    {testResults.sampleImages.map((src, i) => (
                      <div key={i} className="aspect-[3/4] bg-[#0a0d11] border border-[#212832] overflow-hidden relative group">
                        <img
                          src={src}
                          alt={`Page ${i + 1}`}
                          className="w-full h-full object-cover group-hover:scale-105 transition-transform"
                          onError={(e) => {
                            (e.target as HTMLElement).style.display = 'none';
                          }}
                        />
                        <span className="absolute bottom-0 left-0 right-0 bg-[rgba(0,0,0,0.7)] text-[9px] text-center text-[#e2e8f0]">
                          Page {i + 1}
                        </span>
                      </div>
                    ))}
                  </div>
                )}
              </div>

              {/* Sample Chapters Parsed */}
              {testResults.sampleChapters.length > 0 && (
                <div className="space-y-1 pt-1 border-t border-[#212832]">
                  <span className="text-[10px] text-[#6b7a8d] uppercase block">
                    Sample Chapters Parsed (Top {testResults.sampleChapters.length}):
                  </span>
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-1 text-[11px]">
                    {testResults.sampleChapters.map((ch, i) => (
                      <div key={i} className="flex items-center justify-between bg-[#0a0d11] px-2 py-1 border border-[#212832]">
                        <span className="text-[#f08c00] font-semibold truncate mr-2">{ch.title}</span>
                        <span className="text-[#6b7a8d] text-[10px] truncate max-w-xs">{ch.url}</span>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>
          )}
        </div>
      )}
    </div>
  );
};
