/**
 * -----------------------------------------------------------------------------
 * ReaderBuffer.tsx - APEX Tactical Manga & Webtoon In-App Reader (READ)
 * -----------------------------------------------------------------------------
 * High-performance, low-latency in-app media viewer designed for the APEX Console:
 * - Dual Reading Modes: Paged (standard manga flip) vs Webtoon (continuous vertical strip).
 * - Zoom & Scaling: Fit Width, Fit Height, 100% Original.
 * - Chapter Navigation: Prev/Next chapter, dropdown selector, keyboard hotkeys.
 * - Native OS Launcher: [OPEN IN SYSTEM VIEWER] for Windows Photos/CDisplayEx/YACReader.
 * - Unharvested Chapter Handler: Inline [HARVEST NOW] button with live progress tracking.
 * -----------------------------------------------------------------------------
 */

import React, { useState, useEffect, useCallback, useRef } from 'react';
import { apiClient, ChapterPagesDto, ChapterDto, MediaItemDto } from '../../api/apiClient';
import { useScreens } from '../../context/ScreenContext';
import {
  ExternalLink,
  ChevronLeft,
  ChevronRight,
  DownloadCloud,
  RefreshCw,
  BookOpen,
  Layers,
} from 'lucide-react';

export const ReaderBuffer: React.FC = () => {
  const { activeChapterId, setActiveChapterId } = useScreens();

  // Chapter & Page State
  const [chapterData, setChapterData] = useState<ChapterPagesDto | null>(null);
  const [allChapters, setAllChapters] = useState<ChapterDto[]>([]);
  const [currentPage, setCurrentPage] = useState<number>(0);
  const [readingMode, setReadingMode] = useState<'PAGED' | 'WEBTOON'>('PAGED');
  const [zoomMode, setZoomMode] = useState<'FIT_WIDTH' | 'FIT_HEIGHT' | 'ORIGINAL'>('FIT_WIDTH');

  // Loading & Action State
  const [isLoading, setIsLoading] = useState<boolean>(false);
  const [isHarvesting, setIsHarvesting] = useState<boolean>(false);
  const [harvestStatus, setHarvestStatus] = useState<string | null>(null);
  const [systemLauncherMsg, setSystemLauncherMsg] = useState<string | null>(null);

  // Fallback Selector State (if no active chapter loaded)
  const [mangaList, setMangaList] = useState<MediaItemDto[]>([]);
  const [selectedMangaId, setSelectedMangaId] = useState<string>('');

  const containerRef = useRef<HTMLDivElement | null>(null);

  // 1. Fetch chapter pages whenever activeChapterId changes
  const loadChapter = useCallback(async (chapterId: string) => {
    setIsLoading(true);
    setHarvestStatus(null);
    setCurrentPage(0);
    try {
      const data = await apiClient.getChapterPages(chapterId);
      setChapterData(data);

      // If chapter belongs to a manga, fetch all chapters for selector dropdown
      if (data.mangaId) {
        const chapters = await apiClient.getChaptersForManga(data.mangaId);
        setAllChapters(chapters);
      }
    } catch (err: any) {
      console.error('[ReaderBuffer] Failed to load chapter pages:', err);
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    if (activeChapterId) {
      loadChapter(activeChapterId);
    } else {
      // Load fallback manga catalog
      apiClient.getMediaVault('manga').then((items) => {
        setMangaList(items);
        if (items.length > 0) {
          setSelectedMangaId(items[0].id);
          apiClient.getChaptersForManga(items[0].id).then(setAllChapters);
        }
      });
    }
  }, [activeChapterId, loadChapter]);

  // When user switches manga in fallback picker
  const handleSelectManga = async (mangaId: string) => {
    setSelectedMangaId(mangaId);
    const chapters = await apiClient.getChaptersForManga(mangaId);
    setAllChapters(chapters);
  };

  // Keyboard navigation for Paged Mode
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      // Avoid intercepting keystrokes inside text inputs
      if (['INPUT', 'TEXTAREA', 'SELECT'].includes((e.target as HTMLElement).tagName)) {
        return;
      }

      if (readingMode === 'PAGED' && chapterData && chapterData.pageFiles.length > 0) {
        if (e.key === 'ArrowRight' || e.key === 'd' || e.key === 'D') {
          handleNextPage();
        } else if (e.key === 'ArrowLeft' || e.key === 'a' || e.key === 'A') {
          handlePrevPage();
        }
      }

      if (e.key === 'w' || e.key === 'W') {
        setReadingMode((prev) => (prev === 'PAGED' ? 'WEBTOON' : 'PAGED'));
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [readingMode, chapterData, currentPage]);

  const handleNextPage = () => {
    if (!chapterData) return;
    if (currentPage < chapterData.pageFiles.length - 1) {
      setCurrentPage((prev) => prev + 1);
    } else {
      // At last page, check if next chapter exists
      navigateToNextChapter();
    }
  };

  const handlePrevPage = () => {
    if (currentPage > 0) {
      setCurrentPage((prev) => prev - 1);
    } else {
      // At first page, check if prev chapter exists
      navigateToPrevChapter();
    }
  };

  // Next / Prev Chapter navigation
  const currentChapterIndex = allChapters.findIndex((c) => c.id === chapterData?.chapterId);

  const navigateToPrevChapter = () => {
    if (currentChapterIndex > 0) {
      const prevCh = allChapters[currentChapterIndex - 1];
      setActiveChapterId(prevCh.id);
    }
  };

  const navigateToNextChapter = () => {
    if (currentChapterIndex >= 0 && currentChapterIndex < allChapters.length - 1) {
      const nextCh = allChapters[currentChapterIndex + 1];
      setActiveChapterId(nextCh.id);
    }
  };

  // External Native OS Viewer Launcher
  const handleOpenExternal = async () => {
    if (!chapterData?.chapterId) return;
    try {
      setSystemLauncherMsg('LAUNCHING OS VIEWER...');
      await apiClient.openExternalViewer(chapterData.chapterId);
      setSystemLauncherMsg('OPENED IN SYSTEM VIEWER');
      setTimeout(() => setSystemLauncherMsg(null), 3000);
    } catch (err: any) {
      setSystemLauncherMsg('ERROR LAUNCHING VIEWER');
      setTimeout(() => setSystemLauncherMsg(null), 3000);
    }
  };

  // Inline Harvest Trigger for un-downloaded chapters
  const handleHarvestCurrentChapter = async () => {
    if (!chapterData?.chapterId) return;
    setIsHarvesting(true);
    setHarvestStatus('HARVEST INITIATED VIA VIRTUAL THREADS...');
    try {
      await apiClient.harvestChapter(chapterData.chapterId);
      setHarvestStatus('HARVEST COMPLETE! RELOADING PAGES...');
      setTimeout(() => {
        loadChapter(chapterData.chapterId);
        setIsHarvesting(false);
      }, 1500);
    } catch (err: any) {
      setHarvestStatus(`HARVEST FAILED: ${err.message || 'Network error'}`);
      setIsHarvesting(false);
    }
  };

  // Render Image scaling class
  const getImageFitClass = () => {
    switch (zoomMode) {
      case 'FIT_HEIGHT':
        return 'max-h-full w-auto object-contain';
      case 'ORIGINAL':
        return 'max-w-none w-auto';
      case 'FIT_WIDTH':
      default:
        return 'max-w-full w-auto object-contain';
    }
  };

  return (
    <div
      ref={containerRef}
      className="h-full w-full bg-[#0a0d11] text-[#e2e8f0] flex flex-col font-mono text-xs overflow-hidden select-none relative"
    >
      {/* 1. Tactical APEX Reader Header Bar */}
      <div className="bg-[#151b22] border-b border-[#212832] px-2 py-1.5 flex items-center justify-between flex-wrap gap-1 z-30">
        {/* Left: Series Title & Chapter Selector */}
        <div className="flex items-center space-x-2">
          <BookOpen className="w-3.5 h-3.5 text-[#3898ec]" />
          <span className="text-[#3898ec] font-bold tracking-wider text-[11px] truncate max-w-[200px]">
            {chapterData ? chapterData.seriesTitle.toUpperCase() : 'NO CHAPTER SELECTED'}
          </span>

          {allChapters.length > 0 && (
            <select
              value={chapterData?.chapterId || ''}
              onChange={(e) => setActiveChapterId(e.target.value)}
              className="bg-[#11161d] border border-[#212832] text-[#e2e8f0] text-[10px] px-2 py-0.5 outline-none focus:border-[#3898ec]"
            >
              {allChapters.map((ch) => (
                <option key={ch.id} value={ch.id}>
                  CH. {ch.chapterNumber} {ch.title ? `- ${ch.title}` : ''} {ch.downloaded ? '✓' : '(NOT DOWNLOADED)'}
                </option>
              ))}
            </select>
          )}

          {/* Prev/Next Chapter Quick Buttons */}
          <div className="flex items-center space-x-0.5">
            <button
              onClick={navigateToPrevChapter}
              disabled={currentChapterIndex <= 0}
              className="apex-btn-secondary px-1.5 py-0.5 disabled:opacity-30 disabled:cursor-not-allowed"
              title="Previous Chapter"
            >
              <ChevronLeft className="w-3 h-3" />
            </button>
            <button
              onClick={navigateToNextChapter}
              disabled={currentChapterIndex < 0 || currentChapterIndex >= allChapters.length - 1}
              className="apex-btn-secondary px-1.5 py-0.5 disabled:opacity-30 disabled:cursor-not-allowed"
              title="Next Chapter"
            >
              <ChevronRight className="w-3 h-3" />
            </button>
          </div>
        </div>

        {/* Center: Mode & Zoom Toggles */}
        <div className="flex items-center space-x-1">
          {/* Mode Switcher */}
          <div className="bg-[#11161d] p-0.5 border border-[#212832] flex items-center space-x-0.5">
            <button
              onClick={() => setReadingMode('PAGED')}
              className={`px-1.5 py-0.5 text-[9px] font-bold ${
                readingMode === 'PAGED' ? 'bg-[#3898ec] text-[#0a0d11]' : 'text-[#6b7a8d] hover:text-[#e2e8f0]'
              }`}
            >
              PAGED
            </button>
            <button
              onClick={() => setReadingMode('WEBTOON')}
              className={`px-1.5 py-0.5 text-[9px] font-bold ${
                readingMode === 'WEBTOON' ? 'bg-[#3898ec] text-[#0a0d11]' : 'text-[#6b7a8d] hover:text-[#e2e8f0]'
              }`}
            >
              WEBTOON
            </button>
          </div>

          {/* Zoom Modes */}
          <div className="bg-[#11161d] p-0.5 border border-[#212832] flex items-center space-x-0.5">
            <button
              onClick={() => setZoomMode('FIT_WIDTH')}
              className={`px-1.5 py-0.5 text-[9px] font-bold ${
                zoomMode === 'FIT_WIDTH' ? 'bg-[#212832] text-[#f08c00]' : 'text-[#6b7a8d] hover:text-[#e2e8f0]'
              }`}
              title="Fit Width"
            >
              WIDTH
            </button>
            <button
              onClick={() => setZoomMode('FIT_HEIGHT')}
              className={`px-1.5 py-0.5 text-[9px] font-bold ${
                zoomMode === 'FIT_HEIGHT' ? 'bg-[#212832] text-[#f08c00]' : 'text-[#6b7a8d] hover:text-[#e2e8f0]'
              }`}
              title="Fit Height"
            >
              HEIGHT
            </button>
            <button
              onClick={() => setZoomMode('ORIGINAL')}
              className={`px-1.5 py-0.5 text-[9px] font-bold ${
                zoomMode === 'ORIGINAL' ? 'bg-[#212832] text-[#f08c00]' : 'text-[#6b7a8d] hover:text-[#e2e8f0]'
              }`}
              title="100% Original Size"
            >
              1:1
            </button>
          </div>

          {/* Page Indicator (in Paged Mode) */}
          {readingMode === 'PAGED' && chapterData && chapterData.pageFiles.length > 0 && (
            <span className="text-[10px] bg-[#11161d] border border-[#212832] px-2 py-0.5 text-[#3fb950] font-bold">
              {String(currentPage + 1).padStart(2, '0')} / {String(chapterData.pageFiles.length).padStart(2, '0')}
            </span>
          )}
        </div>

        {/* Right: External Viewer Action */}
        <div className="flex items-center space-x-1.5">
          {systemLauncherMsg && (
            <span className="text-[9px] text-[#f08c00] animate-pulse font-bold">{systemLauncherMsg}</span>
          )}
          <button
            onClick={handleOpenExternal}
            disabled={!chapterData?.downloaded}
            className="apex-btn-primary flex items-center space-x-1 py-0.5 px-2 text-[10px] disabled:opacity-30 disabled:cursor-not-allowed"
            title="Open chapter folder in Windows Photos, CDisplayEx, or default system viewer"
          >
            <ExternalLink className="w-3 h-3" />
            <span>SYSTEM VIEWER</span>
          </button>
        </div>
      </div>

      {/* 2. Main Canvas View */}
      <div className="flex-1 relative overflow-auto bg-[#080b0f] flex items-center justify-center p-1">
        {/* Loading Overlay */}
        {isLoading && (
          <div className="flex flex-col items-center justify-center space-y-2 text-[#3898ec]">
            <RefreshCw className="w-6 h-6 animate-spin" />
            <span className="text-[11px] tracking-widest font-bold">LOADING VAULT SECTOR IMAGES...</span>
          </div>
        )}

        {/* Empty State / Fallback Picker if no chapter loaded */}
        {!isLoading && !chapterData && (
          <div className="max-w-md w-full bg-[#11161d] border border-[#212832] p-4 text-center space-y-4">
            <div className="flex justify-center">
              <Layers className="w-8 h-8 text-[#6b7a8d]" />
            </div>
            <div>
              <h3 className="text-sm font-bold text-[#e2e8f0] mb-1">NO ACTIVE CHAPTER SELECTED</h3>
              <p className="text-[11px] text-[#6b7a8d]">
                Select a series and chapter below, or click [READ] from the Media Vault table.
              </p>
            </div>

            {mangaList.length > 0 ? (
              <div className="space-y-2 text-left">
                <div>
                  <label className="text-[10px] text-[#6b7a8d] block mb-1">SERIES CATALOG:</label>
                  <select
                    value={selectedMangaId}
                    onChange={(e) => handleSelectManga(e.target.value)}
                    className="w-full bg-[#0a0d11] border border-[#212832] text-[#e2e8f0] px-2 py-1 text-xs"
                  >
                    {mangaList.map((m) => (
                      <option key={m.id} value={m.id}>
                        {m.title} ({m.count})
                      </option>
                    ))}
                  </select>
                </div>

                <div>
                  <label className="text-[10px] text-[#6b7a8d] block mb-1">AVAILABLE CHAPTERS:</label>
                  <div className="max-h-48 overflow-y-auto space-y-1 bg-[#0a0d11] p-1 border border-[#212832]">
                    {allChapters.map((ch) => (
                      <button
                        key={ch.id}
                        onClick={() => setActiveChapterId(ch.id)}
                        className="w-full flex items-center justify-between p-1.5 hover:bg-[#151b22] text-[10px] border border-transparent hover:border-[#3898ec]"
                      >
                        <span className="text-[#e2e8f0] font-semibold">
                          CH. {ch.chapterNumber} {ch.title ? `- ${ch.title}` : ''}
                        </span>
                        <span
                          className={`text-[9px] px-1 py-0.2 border ${
                            ch.downloaded
                              ? 'border-[#3fb950] text-[#3fb950]'
                              : 'border-[#f08c00] text-[#f08c00]'
                          }`}
                        >
                          {ch.downloaded ? 'DOWNLOADED' : 'ONLINE'}
                        </span>
                      </button>
                    ))}
                  </div>
                </div>
              </div>
            ) : (
              <div className="text-[11px] text-[#6b7a8d]">
                No manga discovered yet. Scout an external URL in the Scraper Monitor first!
              </div>
            )}
          </div>
        )}

        {/* Unharvested Chapter State */}
        {!isLoading && chapterData && !chapterData.downloaded && (
          <div className="max-w-md w-full bg-[#11161d] border border-[#f08c00] p-4 text-center space-y-3">
            <DownloadCloud className="w-8 h-8 text-[#f08c00] mx-auto" />
            <h3 className="text-sm font-bold text-[#e2e8f0]">CHAPTER NOT DOWNLOADED TO LOCAL VAULT</h3>
            <p className="text-[11px] text-[#8a95a5]">
              Chapter {chapterData.chapterNumber} metadata is cataloged, but page image binaries have not yet been
              harvested into storage.
            </p>

            {harvestStatus && (
              <div className="p-2 bg-[#0a0d11] border border-[#212832] text-[10px] text-[#f08c00] animate-pulse">
                {harvestStatus}
              </div>
            )}

            <button
              onClick={handleHarvestCurrentChapter}
              disabled={isHarvesting}
              className="apex-btn-primary py-1.5 px-4 text-xs flex items-center justify-center space-x-1.5 mx-auto"
            >
              <RefreshCw className={`w-3.5 h-3.5 ${isHarvesting ? 'animate-spin' : ''}`} />
              <span>{isHarvesting ? 'HARVESTING VIA V-THREADS...' : 'HARVEST CHAPTER NOW'}</span>
            </button>
          </div>
        )}

        {/* Downloaded: Paged Mode View */}
        {!isLoading && chapterData && chapterData.downloaded && readingMode === 'PAGED' && (
          <div className="h-full w-full flex items-center justify-center relative select-none">
            {chapterData.pageFiles.length > 0 ? (
              <>
                {/* Clickable Left Half for Prev Page */}
                <div
                  onClick={handlePrevPage}
                  className="absolute left-0 top-0 w-1/3 h-full cursor-w-resize z-10 flex items-center pl-4 opacity-0 hover:opacity-100 transition-opacity"
                >
                  <div className="bg-black/60 p-2 border border-[#3898ec] text-[#3898ec]">
                    <ChevronLeft className="w-6 h-6" />
                  </div>
                </div>

                {/* Main Page Image */}
                <div className="h-full w-full flex items-center justify-center overflow-auto p-1">
                  <img
                    src={apiClient.getChapterPageUrl(
                      chapterData.chapterId,
                      chapterData.pageFiles[currentPage]
                    )}
                    alt={`Page ${currentPage + 1}`}
                    className={`${getImageFitClass()} shadow-2xl transition-all`}
                  />
                </div>

                {/* Clickable Right Half for Next Page */}
                <div
                  onClick={handleNextPage}
                  className="absolute right-0 top-0 w-1/3 h-full cursor-e-resize z-10 flex items-center justify-end pr-4 opacity-0 hover:opacity-100 transition-opacity"
                >
                  <div className="bg-black/60 p-2 border border-[#3898ec] text-[#3898ec]">
                    <ChevronRight className="w-6 h-6" />
                  </div>
                </div>

                {/* Bottom Floating Nav Bar */}
                <div className="absolute bottom-3 left-1/2 -translate-x-1/2 bg-[#11161d]/90 border border-[#212832] px-3 py-1 flex items-center space-x-3 z-20 backdrop-blur-sm">
                  <button
                    onClick={handlePrevPage}
                    disabled={currentPage === 0 && currentChapterIndex <= 0}
                    className="text-[#6b7a8d] hover:text-[#3898ec] disabled:opacity-30"
                  >
                    <ChevronLeft className="w-4 h-4" />
                  </button>
                  <span className="text-[10px] text-[#e2e8f0] font-bold">
                    PAGE {currentPage + 1} / {chapterData.pageFiles.length}
                  </span>
                  <button
                    onClick={handleNextPage}
                    disabled={
                      currentPage === chapterData.pageFiles.length - 1 &&
                      currentChapterIndex >= allChapters.length - 1
                    }
                    className="text-[#6b7a8d] hover:text-[#3898ec] disabled:opacity-30"
                  >
                    <ChevronRight className="w-4 h-4" />
                  </button>
                </div>
              </>
            ) : (
              <div className="text-[11px] text-[#f08c00]">
                Chapter marked downloaded, but zero page images found on disk.
              </div>
            )}
          </div>
        )}

        {/* Downloaded: Webtoon Continuous Strip View */}
        {!isLoading && chapterData && chapterData.downloaded && readingMode === 'WEBTOON' && (
          <div className="h-full w-full overflow-y-auto flex flex-col items-center p-2 space-y-1">
            {chapterData.pageFiles.map((filename, idx) => (
              <div key={idx} className="w-full flex justify-center">
                <img
                  src={apiClient.getChapterPageUrl(chapterData.chapterId, filename)}
                  alt={`Page ${idx + 1}`}
                  loading="lazy"
                  className={`${
                    zoomMode === 'FIT_HEIGHT'
                      ? 'max-h-[90vh] w-auto'
                      : zoomMode === 'ORIGINAL'
                      ? 'max-w-none'
                      : 'w-full max-w-3xl'
                  } shadow-md`}
                />
              </div>
            ))}

            {/* End of chapter indicator */}
            <div className="py-6 flex flex-col items-center space-y-2">
              <span className="text-[11px] text-[#6b7a8d]">END OF CHAPTER {chapterData.chapterNumber}</span>
              {currentChapterIndex < allChapters.length - 1 && (
                <button
                  onClick={navigateToNextChapter}
                  className="apex-btn-primary py-1 px-4 text-xs flex items-center space-x-1"
                >
                  <span>NEXT CHAPTER</span>
                  <ChevronRight className="w-3.5 h-3.5" />
                </button>
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
