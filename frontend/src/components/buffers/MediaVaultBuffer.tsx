/**
 * -----------------------------------------------------------------------------
 * MediaVaultBuffer - High-Density Library Catalog (VLT)
 * -----------------------------------------------------------------------------
 * Replicates the data-dense table view seen in the APEX console (e.g. FLEET FLT
 * and PLANET INFO tables):
 * - Sub-tabs: ALL, MANGA, ANIME, MUSIC, GAMES.
 * - Live REST data query across SQLite vault entities.
 * - Interactive APEX Tactical Detail Modal when clicking [VIEW].
 * -----------------------------------------------------------------------------
 */

import React, { useState, useEffect } from 'react';
import { apiClient, MediaItemDto, ChapterDto } from '../../api/apiClient';
import { useScreens } from '../../context/ScreenContext';
import { Search, RefreshCw, Eye, X, BookOpen, DownloadCloud, Trash2, GripHorizontal } from 'lucide-react';

export const MediaVaultBuffer: React.FC = () => {
  const { openReaderForChapter } = useScreens();
  const [activeTab, setActiveTab] = useState<'all' | 'manga' | 'anime' | 'music' | 'games'>('all');
  const [items, setItems] = useState<MediaItemDto[]>([]);
  const [searchFilter, setSearchFilter] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [selectedItem, setSelectedItem] = useState<MediaItemDto | null>(null);

  // Floating / draggable modal state
  const [modalPosition, setModalPosition] = useState<{ x: number; y: number } | null>(null);
  const [isDragging, setIsDragging] = useState(false);
  const [dragOffset, setDragOffset] = useState<{ x: number; y: number }>({ x: 0, y: 0 });
  const [isDeleting, setIsDeleting] = useState(false);

  // Chapter state for selected manga item
  const [itemChapters, setItemChapters] = useState<ChapterDto[]>([]);
  const [loadingChapters, setLoadingChapters] = useState<boolean>(false);
  const [harvestingChapterId, setHarvestingChapterId] = useState<string | null>(null);
  const [harvestAllStatus, setHarvestAllStatus] = useState<{ current: number; total: number } | null>(null);

  const handleSelectItem = (item: MediaItemDto) => {
    setSelectedItem(item);
    setModalPosition(null);
  };

  const handleMouseDown = (e: React.MouseEvent) => {
    if ((e.target as HTMLElement).closest('button')) return;
    setIsDragging(true);
    const modalEl = document.getElementById('media-detail-modal');
    if (modalEl) {
      const rect = modalEl.getBoundingClientRect();
      setDragOffset({
        x: e.clientX - rect.left,
        y: e.clientY - rect.top,
      });
    }
  };

  useEffect(() => {
    const handleMouseMove = (e: MouseEvent) => {
      if (!isDragging) return;
      setModalPosition({
        x: Math.max(10, Math.min(window.innerWidth - 320, e.clientX - dragOffset.x)),
        y: Math.max(10, Math.min(window.innerHeight - 200, e.clientY - dragOffset.y)),
      });
    };

    const handleMouseUp = () => {
      setIsDragging(false);
    };

    if (isDragging) {
      window.addEventListener('mousemove', handleMouseMove);
      window.addEventListener('mouseup', handleMouseUp);
    }
    return () => {
      window.removeEventListener('mousemove', handleMouseMove);
      window.removeEventListener('mouseup', handleMouseUp);
    };
  }, [isDragging, dragOffset]);

  const handleDeleteMedia = async (item: MediaItemDto) => {
    if (!window.confirm(`Are you sure you want to permanently delete "${item.title}" from your vault?`)) {
      return;
    }
    setIsDeleting(true);
    try {
      await apiClient.deleteMedia(item.category, item.id);
      setSelectedItem(null);
      await fetchItems();
    } catch (err: any) {
      alert(`Failed to delete media: ${err.message}`);
    } finally {
      setIsDeleting(false);
    }
  };

  const fetchItems = async () => {
    setIsLoading(true);
    try {
      const data = await apiClient.getMediaVault(activeTab);
      if (data && data.length > 0) {
        setItems(data);
      } else {
        // Sample fallback if backend is offline
        setItems([
          { id: 'm-1', category: 'MANGA', title: 'Solo Leveling', creatorOrAuthor: 'Chugong', status: 'COMPLETED', count: '179 ch', visibility: 'WORKSPACE', description: 'In a world where hunters must battle deadly monsters, weak hunter Sung Jinwoo discovers a quest log that only he can see.' },
          { id: 'm-2', category: 'MANGA', title: 'Berserk', creatorOrAuthor: 'Kentaro Miura', status: 'READING', count: '375 ch', visibility: 'PRIVATE', description: 'Guts, a former mercenary now known as the Black Swordsman, is out for revenge.' },
          { id: 'a-1', category: 'ANIME', title: 'Cowboy Bebop', creatorOrAuthor: 'Shinichiro Watanabe', status: 'FINISHED', count: '1998', visibility: 'PRIVATE', description: 'The futuristic misadventures of an easygoing bounty hunter and his partners.' },
          { id: 'g-1', category: 'GAMES', title: 'Elden Ring', creatorOrAuthor: 'FromSoftware', status: 'PLAYING', count: '2022', visibility: 'WORKSPACE', description: 'Rise, Tarnished, and be guided by grace to brandish the power of the Elden Ring.' },
        ]);
      }
    } catch {
      setItems([]);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchItems();
  }, [activeTab]);

  useEffect(() => {
    if (selectedItem && selectedItem.category === 'MANGA') {
      setLoadingChapters(true);
      apiClient.getChaptersForManga(selectedItem.id).then((ch) => {
        setItemChapters(ch);
        setLoadingChapters(false);
      });
    } else {
      setItemChapters([]);
    }
  }, [selectedItem]);

  const handleHarvestChapter = async (chapterId: string) => {
    setHarvestingChapterId(chapterId);
    try {
      await apiClient.harvestChapter(chapterId);
      if (selectedItem) {
        const updated = await apiClient.getChaptersForManga(selectedItem.id);
        setItemChapters(updated);
      }
    } catch (err) {
      console.error('[MediaVaultBuffer] Chapter harvest failed:', err);
    } finally {
      setHarvestingChapterId(null);
    }
  };

  const handleHarvestAllItemChapters = async () => {
    const unharvested = itemChapters.filter((c) => !c.downloaded);
    if (unharvested.length === 0) return;

    setHarvestAllStatus({ current: 0, total: unharvested.length });
    for (let i = 0; i < unharvested.length; i++) {
      const ch = unharvested[i];
      setHarvestAllStatus({ current: i + 1, total: unharvested.length });
      setHarvestingChapterId(ch.id);
      try {
        await apiClient.harvestChapter(ch.id);
        if (selectedItem) {
          const updated = await apiClient.getChaptersForManga(selectedItem.id);
          setItemChapters(updated);
        }
      } catch (err) {
        console.error(`[MediaVaultBuffer] Batch harvest failed for Ch ${ch.chapterNumber}:`, err);
      }
    }
    setHarvestingChapterId(null);
    setHarvestAllStatus(null);
  };

  const filteredItems = items.filter(
    (i) =>
      i.title.toLowerCase().includes(searchFilter.toLowerCase()) ||
      i.creatorOrAuthor.toLowerCase().includes(searchFilter.toLowerCase())
  );

  return (
    <div className="h-full w-full bg-[#11161d] text-[#e2e8f0] flex flex-col font-mono text-xs overflow-hidden select-none relative">
      {/* Buffer Sub-Header & Category Tabs */}
      <div className="bg-[#151b22] border-b border-[#212832] p-1.5 flex items-center justify-between">
        <div className="flex items-center space-x-1">
          {(['all', 'manga', 'anime', 'music', 'games'] as const).map((tab) => (
            <button
              key={tab}
              onClick={() => setActiveTab(tab)}
              className={`apex-btn-secondary ${activeTab === tab ? 'active' : ''}`}
            >
              {tab.toUpperCase()}
            </button>
          ))}
        </div>

        <button
          onClick={fetchItems}
          className="text-[#6b7a8d] hover:text-[#3898ec] p-1"
          title="Refresh Vault"
        >
          <RefreshCw className={`w-3.5 h-3.5 ${isLoading ? 'animate-spin' : ''}`} />
        </button>
      </div>

      {/* Search Filter Bar (Matching APEX Resource Filter) */}
      <div className="p-1.5 bg-[#0e1217] border-b border-[#212832] flex items-center space-x-2">
        <Search className="w-3.5 h-3.5 text-[#6b7a8d]" />
        <input
          type="text"
          value={searchFilter}
          onChange={(e) => setSearchFilter(e.target.value)}
          placeholder="Filter by title, creator, or metadata..."
          className="w-full bg-[#11161d] border border-[#212832] focus:border-[#3898ec] px-2 py-0.5 text-xs text-[#e2e8f0] outline-none placeholder-[#4b5563]"
        />
      </div>

      {/* High-Density Data Table */}
      <div className="flex-1 overflow-auto">
        <table className="apex-table">
          <thead>
            <tr>
              <th className="w-16">TYPE</th>
              <th>TITLE</th>
              <th>CREATOR / STUDIO</th>
              <th className="w-24">STATUS</th>
              <th className="w-20">COUNT</th>
              <th className="w-20">VISIBILITY</th>
              <th className="w-16 text-center">ACTION</th>
            </tr>
          </thead>
          <tbody>
            {filteredItems.map((item, idx) => (
              <tr key={item.id || idx}>
                <td className="text-[#3898ec] font-bold text-[10px]">[{item.category}]</td>
                <td className="font-semibold text-[#e2e8f0]">{item.title}</td>
                <td className="text-[#8a95a5]">{item.creatorOrAuthor}</td>
                <td>
                  <span
                    className={`px-1.5 py-0.2 text-[9px] font-semibold border ${
                      item.status === 'COMPLETED' || item.status === 'FINISHED'
                        ? 'border-[#3fb950] text-[#3fb950] bg-[rgba(63,185,80,0.1)]'
                        : item.status === 'READING' || item.status === 'PLAYING' || item.status === 'ONGOING'
                        ? 'border-[#3898ec] text-[#3898ec] bg-[rgba(56,152,236,0.1)]'
                        : 'border-[#f08c00] text-[#f08c00] bg-[rgba(240,140,0,0.1)]'
                    }`}
                  >
                    {item.status}
                  </span>
                </td>
                <td className="text-[#e2e8f0] text-[11px]">{item.count}</td>
                <td>
                  <span
                    className={`text-[9px] font-semibold px-1 py-0.2 border ${
                      item.visibility === 'WORKSPACE'
                        ? 'border-[#f1c40f] text-[#f1c40f]'
                        : 'border-[#6b7a8d] text-[#6b7a8d]'
                    }`}
                  >
                    {item.visibility}
                  </span>
                </td>
                <td className="text-center">
                  <div className="flex items-center space-x-1 justify-center">
                    <button
                      onClick={() => handleSelectItem(item)}
                      className="apex-btn-primary flex items-center justify-center space-x-1 py-0.5 px-2"
                      title="View Details & Chapters"
                    >
                      <Eye className="w-2.5 h-2.5" />
                      <span>VIEW</span>
                    </button>
                    <button
                      onClick={() => handleDeleteMedia(item)}
                      className="apex-btn-secondary text-[#e05656] hover:bg-[#e05656] hover:text-white p-1"
                      title="Delete from Vault"
                    >
                      <Trash2 className="w-3 h-3" />
                    </button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* APEX Tactical Detail Floating Modal */}
      {selectedItem && (
        <div className="fixed inset-0 bg-black/50 z-50 pointer-events-none">
          <div
            id="media-detail-modal"
            style={
              modalPosition
                ? { top: `${modalPosition.y}px`, left: `${modalPosition.x}px`, transform: 'none' }
                : { top: '50%', left: '50%', transform: 'translate(-50%, -50%)' }
            }
            className="fixed bg-[#11161d] border border-[#3898ec] w-full max-w-lg shadow-2xl font-mono text-xs flex flex-col max-h-[85vh] pointer-events-auto select-none"
          >
            {/* Draggable Header */}
            <div
              onMouseDown={handleMouseDown}
              className="flex items-center justify-between border-b border-[#212832] p-2.5 cursor-move bg-[#151b22]"
            >
              <div className="flex items-center space-x-2">
                <GripHorizontal className="w-4 h-4 text-[#3898ec]" />
                <span className="text-[#3898ec] font-bold text-xs">[{selectedItem.category}]</span>
                <span className="text-[#e2e8f0] font-bold text-sm truncate max-w-[280px]">{selectedItem.title}</span>
              </div>
              <button
                onClick={() => setSelectedItem(null)}
                className="text-[#6b7a8d] hover:text-[#e2e8f0] p-1"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            {/* Scrollable Body */}
            <div className="flex-1 overflow-y-auto p-3 space-y-2.5 text-[11px]">
              <div className="grid grid-cols-2 gap-2 bg-[#0c0f13] p-2 border border-[#212832]">
                <div>
                  <span className="text-[#6b7a8d] block text-[10px]">CREATOR / ARTIST:</span>
                  <span className="text-[#e2e8f0] font-semibold">{selectedItem.creatorOrAuthor}</span>
                </div>
                <div>
                  <span className="text-[#6b7a8d] block text-[10px]">STATUS:</span>
                  <span className="text-[#f08c00] font-semibold">{selectedItem.status}</span>
                </div>
                <div>
                  <span className="text-[#6b7a8d] block text-[10px]">PROGRESS / METRICS:</span>
                  <span className="text-[#3898ec] font-semibold">{selectedItem.count}</span>
                </div>
                <div>
                  <span className="text-[#6b7a8d] block text-[10px]">STORAGE VISIBILITY:</span>
                  <span className="text-[#3fb950] font-semibold">{selectedItem.visibility}</span>
                </div>
              </div>

              {selectedItem.description && (
                <div>
                  <span className="text-[#6b7a8d] block text-[10px] uppercase mb-0.5">Synopsis / Notes:</span>
                  <p className="text-[#8a95a5] bg-[#0c0f13] p-2 border border-[#212832] leading-relaxed">
                    {selectedItem.description}
                  </p>
                </div>
              )}

              {/* Manga Chapter Catalog with direct [READ] and [HARVEST] actions */}
              {selectedItem.category === 'MANGA' && (
                <div className="mt-2 border-t border-[#212832] pt-2">
                  <div className="flex items-center justify-between mb-1">
                    <span className="text-[#3898ec] font-bold text-[10px] uppercase">
                      CHAPTER CATALOG ({itemChapters.length})
                    </span>
                    <div className="flex items-center space-x-2">
                      {itemChapters.some((c) => !c.downloaded) && (
                        <button
                          onClick={handleHarvestAllItemChapters}
                          disabled={harvestAllStatus !== null}
                          className="apex-btn-primary py-0.5 px-2 text-[9px] flex items-center space-x-1 disabled:opacity-50"
                          title="Harvest all un-downloaded chapters"
                        >
                          <DownloadCloud className={`w-3 h-3 ${harvestAllStatus ? 'animate-bounce' : ''}`} />
                          <span>
                            {harvestAllStatus
                              ? `DOWNLOADING (${harvestAllStatus.current}/${harvestAllStatus.total})...`
                              : `DOWNLOAD ALL (${itemChapters.filter((c) => !c.downloaded).length})`}
                          </span>
                        </button>
                      )}
                      {loadingChapters && <RefreshCw className="w-3 h-3 text-[#3898ec] animate-spin" />}
                    </div>
                  </div>
                  <div className="max-h-48 overflow-y-auto bg-[#0c0f13] border border-[#212832]">
                    {itemChapters.length === 0 && !loadingChapters ? (
                      <div className="p-2 text-[10px] text-[#6b7a8d]">No chapters cataloged yet.</div>
                    ) : (
                      <table className="apex-table text-[10px]">
                        <thead>
                          <tr>
                            <th className="w-12">CH #</th>
                            <th>TITLE</th>
                            <th className="w-16">PAGES</th>
                            <th className="w-20">STATUS</th>
                            <th className="w-24 text-center">ACTION</th>
                          </tr>
                        </thead>
                        <tbody>
                          {itemChapters.map((ch) => (
                            <tr key={ch.id}>
                              <td className="font-bold text-[#3898ec]">{ch.chapterNumber}</td>
                              <td className="text-[#e2e8f0] truncate max-w-[140px]">
                                {ch.title || `Chapter ${ch.chapterNumber}`}
                              </td>
                              <td className="text-[#8a95a5]">{ch.pageCount || '-'}</td>
                              <td>
                                <span
                                  className={`px-1 py-0.2 text-[8px] font-semibold border ${
                                    ch.downloaded
                                      ? 'border-[#3fb950] text-[#3fb950]'
                                      : 'border-[#f08c00] text-[#f08c00]'
                                  }`}
                                >
                                  {ch.downloaded ? 'DOWNLOADED' : 'AVAILABLE'}
                                </span>
                              </td>
                              <td className="text-center">
                                {ch.downloaded ? (
                                  <button
                                    onClick={() => {
                                      setSelectedItem(null);
                                      openReaderForChapter(ch.id);
                                    }}
                                    className="apex-btn-primary py-0.2 px-2 text-[9px] flex items-center justify-center space-x-1 w-full"
                                  >
                                    <BookOpen className="w-2.5 h-2.5" />
                                    <span>READ</span>
                                  </button>
                                ) : (
                                  <button
                                    onClick={() => handleHarvestChapter(ch.id)}
                                    disabled={harvestingChapterId === ch.id}
                                    className="apex-btn-secondary py-0.2 px-1 text-[9px] flex items-center justify-center space-x-1 w-full disabled:opacity-50"
                                  >
                                    <DownloadCloud
                                      className={`w-2.5 h-2.5 ${
                                        harvestingChapterId === ch.id ? 'animate-bounce text-[#f08c00]' : ''
                                      }`}
                                    />
                                    <span>{harvestingChapterId === ch.id ? 'HARVESTING' : 'HARVEST'}</span>
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

              <div className="text-[10px] text-[#4b5563] pt-1">
                Vault UUID: <span className="font-mono text-[#6b7a8d]">{selectedItem.id}</span>
              </div>
            </div>

            {/* Footer with Delete and Close */}
            <div className="flex justify-between items-center p-2.5 border-t border-[#212832] bg-[#0e1217]">
              <button
                onClick={() => handleDeleteMedia(selectedItem)}
                disabled={isDeleting}
                className="apex-btn-secondary px-3 py-1 text-[#e05656] border-[#e05656] hover:bg-[#e05656] hover:text-white flex items-center space-x-1"
              >
                <Trash2 className="w-3.5 h-3.5" />
                <span>{isDeleting ? 'DELETING...' : 'DELETE MEDIA'}</span>
              </button>
              <button
                onClick={() => setSelectedItem(null)}
                className="apex-btn-secondary px-3 py-1"
              >
                CLOSE
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
