/**
 * -----------------------------------------------------------------------------
 * API Client Service for The Archive - APEX Console
 * -----------------------------------------------------------------------------
 * This module provides typed asynchronous fetch wrappers for communicating with
 * the Spring Boot backend REST endpoints running on port 61069.
 *
 * All requests route through the Vite reverse proxy configured in vite.config.ts,
 * so relative URLs like '/api/...' automatically forward to the backend without
 * triggering Cross-Origin Resource Sharing (CORS) preflight restrictions.
 * -----------------------------------------------------------------------------
 */

export interface UserLayoutDto {
  id?: number;
  layoutName: string;
  layoutJson: string;
  isDefault: boolean;
  commandBarPosition: 'TOP' | 'BOTTOM';
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
}

export interface ActivityDto {
  id: number;
  username: string;
  action: string;
  entityType: string;
  details: string;
  timestamp: string;
}

export interface ChatMessageDto {
  id?: number;
  roomId: string;
  sender: string;
  content: string;
  type: 'CHAT' | 'JOIN' | 'LEAVE' | 'SYSTEM';
  timestamp?: string;
}

export const apiClient = {
  /**
   * Fetches all saved screen layouts from the backend database.
   */
  async getAllLayouts(): Promise<UserLayoutDto[]> {
    const res = await fetch('/api/layouts/all');
    if (!res.ok) throw new Error(`Failed to load layouts: ${res.statusText}`);
    return res.json();
  },

  /**
   * Fetches the designated default screen layout.
   */
  async getDefaultLayout(): Promise<UserLayoutDto | null> {
    const res = await fetch('/api/layouts/default');
    if (res.status === 404) return null;
    if (!res.ok) throw new Error(`Failed to load default layout: ${res.statusText}`);
    return res.json();
  },

  /**
   * Saves or updates a screen layout in the SQLite vault.
   */
  async saveLayout(layout: UserLayoutDto): Promise<UserLayoutDto> {
    const res = await fetch('/api/layouts/save', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(layout),
    });
    if (!res.ok) throw new Error(`Failed to save layout: ${res.statusText}`);
    return res.json();
  },

  /**
   * Deletes a saved screen layout by name.
   */
  async deleteLayout(layoutName: string): Promise<void> {
    const res = await fetch(`/api/layouts/${encodeURIComponent(layoutName)}`, {
      method: 'DELETE',
    });
    if (!res.ok) throw new Error(`Failed to delete layout: ${res.statusText}`);
  },

  /**
   * Dispatches a raw APEX command string to the backend CLI execution engine.
   */
  async executeCommand(command: string): Promise<TerminalExecutionResponse> {
    const res = await fetch('/api/terminal/execute', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ command }),
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
          fetch('/api/manga').then((r) => (r.ok ? r.json() : [])).catch(() => []),
          fetch('/api/anime').then((r) => (r.ok ? r.json() : [])).catch(() => []),
          fetch('/api/music').then((r) => (r.ok ? r.json() : [])).catch(() => []),
          fetch('/api/games').then((r) => (r.ok ? r.json() : [])).catch(() => []),
        ]);
        return [
          ...manga.map((m: any) => ({
            id: m.id,
            category: 'MANGA',
            title: m.title,
            creatorOrAuthor: m.author || m.artist || 'Unknown',
            status: m.status || 'READING',
            count: m.chapters ? `${m.chapters.length} ch` : '0 ch',
            visibility: m.visibility || 'PRIVATE',
            description: m.description,
            genres: m.genres,
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

      const res = await fetch(`/api/${type}`);
      if (!res.ok) return [];
      const raw = await res.json();
      return raw.map((item: any) => ({
        id: item.id,
        category: type.toUpperCase(),
        title: item.title,
        creatorOrAuthor: item.author || item.artist || item.studio || item.developer || 'Unknown',
        status: item.status || item.playStatus || item.albumType || 'ACTIVE',
        count: item.chapters ? `${item.chapters.length} ch` : item.releaseYear ? `${item.releaseYear}` : '1',
        visibility: item.visibility || 'PRIVATE',
        description: item.description,
        genres: item.genres,
        platforms: item.platforms,
      }));
    } catch {
      return [];
    }
  },

  /**
   * Scouts a target web URL using Jsoup to discover series title and chapters.
   */
  async scoutSeries(url: string): Promise<any> {
    const res = await fetch('/api/scraper/scout', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ url }),
    });
    if (!res.ok) throw new Error(`Scouting failed: ${res.statusText}`);
    return res.json();
  },

  /**
   * Retrieves workspace activity logs for the SYS.LOG buffer.
   */
  async getActivities(): Promise<ActivityDto[]> {
    try {
      const res = await fetch('/api/workspace/activities');
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
      const res = await fetch(`/api/chat/history/${encodeURIComponent(roomId)}`);
      if (!res.ok) return [];
      return res.json();
    } catch {
      return [];
    }
  },
};
