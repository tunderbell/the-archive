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
  id: number;
  title: string;
  creatorOrAuthor: string;
  status: string;
  chapterOrEpisodeCount: number;
  syncedWithWorkspace: boolean;
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
    return res.json();
  },

  /**
   * Retrieves media vault entries for the high-density data buffer.
   */
  async getMediaVault(type: 'manga' | 'anime' | 'music' | 'games'): Promise<MediaItemDto[]> {
    try {
      const res = await fetch(`/api/${type}`);
      if (!res.ok) return [];
      return res.json();
    } catch {
      return [];
    }
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
  }
};
