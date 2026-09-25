/**
 * -----------------------------------------------------------------------------
 * apiClient.ts - Centralized REST Client for The Archive
 * -----------------------------------------------------------------------------
 * Provides typed HTTP API methods connecting the React frontend to the
 * Spring Boot backend on port 61069.
 *
 * In browser dev mode, Vite reverse-proxies /api to http://localhost:61069.
 * In standalone Electron (file:// protocol), API_BASE routes requests directly
 * to http://localhost:61069.
 * -----------------------------------------------------------------------------
 */

export interface UserLayoutDto {
  id?: string;
  layoutName: string;
  name?: string;
  isDefault?: boolean;
  layoutJson: string;
  commandBarPosition?: 'TOP' | 'BOTTOM';
  activeTheme?: string;
}

export interface TerminalExecutionResponse {
  command: string;
  output: string;
  executionTimeMs: number;
  success: boolean;
}

export interface MediaItemDto {
  id: string;
  category: string;
  title: string;
  creatorOrAuthor: string;
  status: string;
  count: string;
  visibility: string;
  description?: string;
  genres?: string[];
  platforms?: string[];
  lastReadChapter?: number;
  lastReadPage?: number;
  readingStatus?: string;
}

export interface ActivityDto {
  id: string | number;
  actor?: string;
  username?: string;
  action: string;
  mediaDomain?: string;
  entityType?: string;
  details: string;
  timestamp: string;
}

export interface ChatMessageDto {
  id?: number | string;
  roomId: string;
  sender: string;
  content: string;
  type: 'CHAT' | 'JOIN' | 'LEAVE' | 'SYSTEM' | 'SYSTEM_ALERT';
  timestamp?: string;
}

export interface ScraperTemplateDto {
  id?: string;
  domainName: string;
  name: string;
  titleSelector: string;
  authorSelector?: string;
  descriptionSelector?: string;
  coverImageSelector?: string;
  chapterListSelector: string;
  chapterTitleSelector?: string;
  imageSelector: string;
  requiresJs: boolean;
  rateLimitMs?: number;
}

export interface TestSelectorResponse {
  domain: string;
  url: string;
  title: string;
  chapterCount: number;
  sampleChapters: Array<{ title: string; url: string }>;
  imageCount: number;
  sampleImages: string[];
}

export interface ChapterDto {
  id: string;
  chapterNumber: number;
  title?: string;
  sourceUrl?: string;
  storagePath?: string | null;
  downloaded: boolean;
  pageCount?: number;
  isRead?: boolean;
  lastReadPage?: number;
  cbzPath?: string | null;
}

export interface ChapterPagesDto {
  chapterId: string;
  mangaId: string;
  seriesTitle: string;
  chapterNumber: number;
  chapterTitle?: string;
  downloaded: boolean;
  pageCount: number;
  pageFiles: string[];
  isRead?: boolean;
  lastReadPage?: number;
  cbzPath?: string | null;
}

const API_BASE =
  typeof window !== 'undefined' && window.location.protocol === 'file:'
    ? 'http://localhost:61069'
    : '';

export const apiClient = {
  getBaseUrl(): string {
    return API_BASE;
  },

  /**
   * Fetches all saved screen layouts from the backend database.
   */
  async getAllLayouts(): Promise<UserLayoutDto[]> {
    const res = await fetch(`${API_BASE}/api/ui/layout`);
    if (!res.ok) throw new Error(`Failed to load layouts: ${res.statusText}`);
    return res.json();
  },

  /**
   * Fetches the designated default screen layout.
   */
  async getDefaultLayout(): Promise<UserLayoutDto | null> {
    const res = await fetch(`${API_BASE}/api/ui/layout/default`);
    if (res.status === 404) return null;
    if (!res.ok) throw new Error(`Failed to load default layout: ${res.statusText}`);
    return res.json();
  },

  /**
   * Saves or updates a screen layout in the SQLite vault.
   */
  async saveLayout(layout: UserLayoutDto): Promise<UserLayoutDto> {
    const res = await fetch(`${API_BASE}/api/ui/layout`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        name: layout.layoutName || layout.name,
        json: layout.layoutJson,
        commandBarPosition: layout.commandBarPosition || 'TOP',
      }),
    });
    if (!res.ok) throw new Error(`Failed to save layout: ${res.statusText}`);
    return res.json();
  },

  /**
   * Deletes a saved screen layout by name.
   */
  async deleteLayout(layoutName: string): Promise<void> {
    const res = await fetch(`${API_BASE}/api/ui/layout/${encodeURIComponent(layoutName)}`, {
      method: 'DELETE',
    });
    if (!res.ok) throw new Error(`Failed to delete layout: ${res.statusText}`);
  },

  /**
   * Dispatches a raw APEX command string to the backend CLI execution engine.
   */
  async executeCommand(command: string, source: 'BAR' | 'TERM' = 'BAR'): Promise<TerminalExecutionResponse> {
    const res = await fetch(`${API_BASE}/api/terminal/execute`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ command, source }),
    });
    if (!res.ok) throw new Error(`Command execution failed: ${res.statusText}`);
    const data = await res.json();
    return {
      command: data.command || command,
      output: data.output || '',
      executionTimeMs: data.executionTimeMs ?? 0,
      success: data.success ?? (!data.output?.startsWith('ERROR') && !data.output?.startsWith('[ERROR]') && !data.output?.startsWith('Unknown APEX command')),
    };
  },

  /**
   * Retrieves media vault entries for the high-density data buffer across all categories.
   */
  async getMediaVault(type: 'all' | 'manga' | 'anime' | 'music' | 'games'): Promise<MediaItemDto[]> {
    try {
      if (type === 'all') {
        const [manga, anime, music, games] = await Promise.all([
          fetch(`${API_BASE}/api/manga`).then((r) => (r.ok ? r.json() : [])).catch(() => []),
          fetch(`${API_BASE}/api/anime`).then((r) => (r.ok ? r.json() : [])).catch(() => []),
          fetch(`${API_BASE}/api/music`).then((r) => (r.ok ? r.json() : [])).catch(() => []),
          fetch(`${API_BASE}/api/games`).then((r) => (r.ok ? r.json() : [])).catch(() => []),
        ]);
        return [
          ...manga.map((m: any) => ({
            id: m.id,
            category: 'MANGA',
            title: m.title,
            creatorOrAuthor: m.author || m.artist || 'Unknown',
            status: m.readingStatus || m.status || 'READING',
            count: m.totalChapters ? `${m.totalChapters} ch` : (m.chapters ? `${m.chapters.length} ch` : '0 ch'),
            visibility: m.visibility || 'PRIVATE',
            description: m.description,
            genres: m.genres,
            lastReadChapter: m.lastReadChapter,
            lastReadPage: m.lastReadPage,
            readingStatus: m.readingStatus,
          })),
          ...anime.map((a: any) => ({
            id: a.id,
            category: 'ANIME',
            title: a.title,
            creatorOrAuthor: a.studio || a.director || 'Unknown',
            status: a.status || 'FINISHED',
            count: a.releaseYear ? `${a.releaseYear}` : 'TV',
            visibility: a.visibility || 'PRIVATE',
            description: a.description,
            genres: a.genres,
          })),
          ...music.map((al: any) => ({
            id: al.id,
            category: 'MUSIC',
            title: al.title,
            creatorOrAuthor: al.artist || 'Unknown',
            status: al.albumType || 'ALBUM',
            count: al.releaseYear ? `${al.releaseYear}` : 'LP',
            visibility: al.visibility || 'PRIVATE',
            description: al.description,
            genres: al.genres,
          })),
          ...games.map((g: any) => ({
            id: g.id,
            category: 'GAMES',
            title: g.title,
            creatorOrAuthor: g.developer || g.publisher || 'Unknown',
            status: g.playStatus || 'PLAYING',
            count: g.releaseYear ? `${g.releaseYear}` : 'PC',
            visibility: g.visibility || 'PRIVATE',
            description: g.description,
            platforms: g.platforms,
          })),
        ];
      }

      const res = await fetch(`${API_BASE}/api/${type}`);
      if (!res.ok) return [];
      const raw = await res.json();
      return raw.map((item: any) => ({
        id: item.id,
        category: type.toUpperCase(),
        title: item.title,
        creatorOrAuthor: item.author || item.artist || item.studio || item.developer || 'Unknown',
        status: item.readingStatus || item.status || item.playStatus || item.albumType || 'ACTIVE',
        count: item.totalChapters ? `${item.totalChapters} ch` : (item.chapters ? `${item.chapters.length} ch` : item.releaseYear ? `${item.releaseYear}` : '1'),
        visibility: item.visibility || 'PRIVATE',
        description: item.description,
        genres: item.genres,
        platforms: item.platforms,
        lastReadChapter: item.lastReadChapter,
        lastReadPage: item.lastReadPage,
        readingStatus: item.readingStatus,
      }));
    } catch {
      return [];
    }
  },

  /**
   * Scouts a target web URL using Jsoup to discover series title and chapters.
   */
  async scoutSeries(url: string): Promise<any> {
    const res = await fetch(`${API_BASE}/api/scraper/scout`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ url }),
    });
    if (!res.ok) {
      const errData = await res.json().catch(() => null);
      throw new Error(errData?.error || `Scouting failed: ${res.statusText}`);
    }
    return res.json();
  },

  /**
   * Tests arbitrary CSS selectors against a live URL for the Wizard.
   */
  async testSelector(payload: {
    url: string;
    titleSelector?: string;
    chapterListSelector?: string;
    imageSelector?: string;
    requiresJs?: boolean;
  }): Promise<TestSelectorResponse> {
    const res = await fetch(`${API_BASE}/api/scraper/test-selector`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });
    if (!res.ok) {
      const errData = await res.json().catch(() => null);
      throw new Error(errData?.error || `Selector test failed: ${res.statusText}`);
    }
    return res.json();
  },

  /**
   * Triggers parallel harvest for a chapter entity by UUID.
   */
  async harvestChapter(chapterId: string): Promise<any> {
    const res = await fetch(`${API_BASE}/api/scraper/harvest/${encodeURIComponent(chapterId)}`, {
      method: 'POST',
    });
    if (!res.ok) throw new Error(`Harvest failed: ${res.statusText}`);
    return res.json();
  },

  /**
   * Retrieves all registered site recipes from the backend.
   */
  async getAllTemplates(): Promise<ScraperTemplateDto[]> {
    try {
      const res = await fetch(`${API_BASE}/api/scraper/template`);
      if (!res.ok) return [];
      return res.json();
    } catch {
      return [];
    }
  },

  /**
   * Saves or updates a site recipe template in the SQLite database.
   */
  async saveTemplate(template: ScraperTemplateDto): Promise<ScraperTemplateDto> {
    const res = await fetch(`${API_BASE}/api/scraper/template`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(template),
    });
    if (!res.ok) throw new Error(`Failed to save template: ${res.statusText}`);
    return res.json();
  },

  /**
   * Retrieves workspace activity logs for the SYS.LOG buffer.
   */
  async getActivities(): Promise<ActivityDto[]> {
    try {
      const res = await fetch(`${API_BASE}/api/workspace/activities`);
      if (!res.ok) return [];
      return res.json();
    } catch {
      return [];
    }
  },

  /**
   * Retrieves chat message history for the collaboration buffer.
   */
  async getChatHistory(roomId: string = 'global'): Promise<ChatMessageDto[]> {
    try {
      const res = await fetch(`${API_BASE}/api/chat/history/${encodeURIComponent(roomId)}`);
      if (!res.ok) return [];
      const data = await res.json();
      return data.map((m: any) => ({
        id: m.id,
        roomId: m.channel || m.roomId || 'global',
        sender: m.sender,
        content: m.content,
        type: m.messageType || m.type || 'CHAT',
        timestamp: m.timestamp ? new Date(m.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : undefined,
      }));
    } catch {
      return [];
    }
  },

  /**
   * Retrieves all chapters for a manga series.
   */
  async getChaptersForManga(mangaId: string): Promise<ChapterDto[]> {
    try {
      const res = await fetch(`${API_BASE}/api/manga/${mangaId}/chapters`);
      if (!res.ok) return [];
      return res.json();
    } catch {
      return [];
    }
  },

  /**
   * Retrieves page image list and metadata for a chapter.
   */
  async getChapterPages(chapterId: string): Promise<ChapterPagesDto> {
    const res = await fetch(`${API_BASE}/api/manga/chapters/${chapterId}/pages`);
    if (!res.ok) throw new Error('Failed to fetch chapter pages');
    return res.json();
  },

  /**
   * Builds the direct image URL for a chapter page.
   */
  getChapterPageUrl(chapterId: string, filename: string): string {
    return `${API_BASE}/api/manga/chapters/${chapterId}/pages/${encodeURIComponent(filename)}`;
  },

  /**
   * Launches the OS native viewer / file manager for a chapter.
   */
  async openExternalViewer(chapterId: string): Promise<{ status: string; message: string }> {
    const res = await fetch(`${API_BASE}/api/manga/chapters/${chapterId}/open-external`, {
      method: 'POST',
    });
    if (!res.ok) throw new Error('Failed to open in external viewer');
    return res.json();
  },

  /**
   * Deletes a media entity from the SQLite vault by category and UUID.
   */
  async deleteMedia(category: string, id: string): Promise<void> {
    let endpoint = `${API_BASE}/api/manga/`;
    const cat = category.toUpperCase();
    if (cat === 'ANIME') endpoint = `${API_BASE}/api/anime/`;
    else if (cat === 'MUSIC') endpoint = `${API_BASE}/api/music/`;
    else if (cat === 'GAMES' || cat === 'VIDEO_GAMES') endpoint = `${API_BASE}/api/games/`;

    const res = await fetch(`${endpoint}${id}`, { method: 'DELETE' });
    if (!res.ok) throw new Error(`Failed to delete media item: ${res.statusText}`);
  },

  /**
   * Saves reading progress/bookmark for a chapter and parent manga.
   */
  async saveReadingProgress(chapterId: string, page: number, isRead?: boolean): Promise<any> {
    const res = await fetch(`${API_BASE}/api/manga/chapters/${chapterId}/progress`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ page, isRead }),
    });
    if (!res.ok) throw new Error('Failed to save reading progress');
    return res.json();
  },

  /**
   * Toggles the read state of a chapter.
   */
  async toggleChapterRead(chapterId: string): Promise<any> {
    const res = await fetch(`${API_BASE}/api/manga/chapters/${chapterId}/read-toggle`, {
      method: 'POST',
    });
    if (!res.ok) throw new Error('Failed to toggle chapter read state');
    return res.json();
  },

  /**
   * Updates user reading status for a manga series.
   */
  async updateReadingStatus(mangaId: string, status: string): Promise<any> {
    const res = await fetch(`${API_BASE}/api/manga/${mangaId}/reading-status`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ status }),
    });
    if (!res.ok) throw new Error('Failed to update reading status');
    return res.json();
  },

  /**
   * Packages downloaded chapter images into a standard .cbz archive.
   */
  async packageChapterCbz(chapterId: string): Promise<{ status: string; cbzPath: string; fileName: string }> {
    const res = await fetch(`${API_BASE}/api/manga/chapters/${chapterId}/package-cbz`, {
      method: 'POST',
    });
    if (!res.ok) throw new Error('Failed to package CBZ archive');
    return res.json();
  },

  /**
   * Returns the direct download URL for a chapter's .cbz archive.
   */
  getCbzDownloadUrl(chapterId: string): string {
    return `${API_BASE}/api/manga/chapters/${chapterId}/cbz`;
  },
};
