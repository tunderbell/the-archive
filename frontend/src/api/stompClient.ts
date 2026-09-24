/**
 * -----------------------------------------------------------------------------
 * WebSocket STOMP Client for The Archive - APEX Console
 * -----------------------------------------------------------------------------
 * Manages the real-time WebSocket connection to Spring Boot (/ws endpoint).
 *
 * Resilient Architecture:
 * - Queue-Based Subscriptions: Subscribing while the backend is offline or
 *   reconnecting will NEVER throw an exception. Subscriptions are safely queued
 *   and automatically bound when the connection is established.
 * - Auto-Reconnection: Automatically reconnects when the Spring Boot backend
 *   starts up.
 * -----------------------------------------------------------------------------
 */

import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

type MessageCallback = (message: any) => void;

interface QueuedSubscription {
  id: string;
  destination: string;
  callback: MessageCallback;
  activeSub?: StompSubscription;
}

class ArchiveStompClient {
  private client: Client;
  private isConnected: boolean = false;
  private connectionListeners: Array<(connected: boolean) => void> = [];
  private subscriptions: Map<string, QueuedSubscription> = new Map();

  constructor() {
    this.client = new Client({
      // We supply a webSocketFactory function using SockJS to point to our reverse proxy /ws
      webSocketFactory: () => new SockJS('/ws'),
      debug: (_msg: string) => {
        // Uncomment for deep frame debugging:
        // console.debug('[STOMP Frame]:', _msg);
      },
      reconnectDelay: 4000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
    });

    this.client.onConnect = () => {
      this.isConnected = true;
      this.notifyConnectionState(true);
      console.log('[STOMP] Connected to The Archive backend.');
      this.bindAllSubscriptions();
    };

    this.client.onDisconnect = () => {
      this.isConnected = false;
      this.notifyConnectionState(false);
      console.warn('[STOMP] Disconnected from The Archive backend.');
    };

    this.client.onWebSocketError = (event) => {
      // Suppress fatal throws when backend is offline
      this.isConnected = false;
      this.notifyConnectionState(false);
      console.warn('[STOMP] WebSocket offline, will retry in background:', event.type);
    };

    this.client.onStompError = (frame) => {
      console.error('[STOMP Error]:', frame.headers['message'], frame.body);
    };
  }

  /**
   * Activates the STOMP client connection.
   */
  public activate(): void {
    if (!this.client.active) {
      try {
        this.client.activate();
      } catch (err) {
        console.warn('[STOMP] Activation deferred:', err);
      }
    }
  }

  /**
   * Deactivates the STOMP client connection.
   */
  public deactivate(): void {
    if (this.client.active) {
      try {
        this.client.deactivate();
      } catch (err) {
        console.warn('[STOMP] Deactivation warning:', err);
      }
    }
  }

  /**
   * Safely registers a subscription for a destination topic.
   * If not connected, queues the subscription and binds when connected.
   */
  private safeSubscribe(destination: string, callback: MessageCallback) {
    const subId = `sub-${Date.now()}-${Math.random()}`;
    const queued: QueuedSubscription = { id: subId, destination, callback };

    if (this.isConnected && this.client.connected) {
      try {
        queued.activeSub = this.client.subscribe(destination, (msg: IMessage) => {
          try {
            callback(JSON.parse(msg.body));
          } catch {
            callback(msg.body);
          }
        });
      } catch (err) {
        console.warn(`[STOMP] Delayed subscription for ${destination}:`, err);
      }
    }

    this.subscriptions.set(subId, queued);

    return {
      unsubscribe: () => {
        const item = this.subscriptions.get(subId);
        if (item?.activeSub) {
          try {
            item.activeSub.unsubscribe();
          } catch {}
        }
        this.subscriptions.delete(subId);
      },
    };
  }

  /**
   * Re-binds all queued subscriptions upon successful STOMP broker connection.
   */
  private bindAllSubscriptions(): void {
    this.subscriptions.forEach((sub) => {
      if (!sub.activeSub && this.client.connected) {
        try {
          sub.activeSub = this.client.subscribe(sub.destination, (msg: IMessage) => {
            try {
              sub.callback(JSON.parse(msg.body));
            } catch {
              sub.callback(msg.body);
            }
          });
        } catch (err) {
          console.warn(`[STOMP] Failed to bind ${sub.destination}:`, err);
        }
      }
    });
  }

  /**
   * Subscribes to real-time terminal output streamed from CommandExecutionService.
   */
  public subscribeToTerminal(callback: MessageCallback) {
    return this.safeSubscribe('/topic/terminal.output', callback);
  }

  /**
   * Subscribes to real-time workspace activity audit log events.
   */
  public subscribeToActivities(callback: MessageCallback) {
    return this.safeSubscribe('/topic/workspace.activity', callback);
  }

  /**
   * Sends an interactive command over WebSocket to /app/terminal.command.
   */
  public sendTerminalCommand(command: string): void {
    if (!this.isConnected || !this.client.connected) {
      console.warn('[STOMP] Terminal command queued/dropped: Backend offline');
      return;
    }
    try {
      this.client.publish({
        destination: '/app/terminal.command',
        body: JSON.stringify({ command }),
      });
    } catch (err) {
      console.warn('[STOMP] Failed to publish terminal command:', err);
    }
  }

  /**
   * Subscribes to a collaboration chat room (e.g. 'global').
   */
  public subscribeToChatRoom(roomId: string, callback: MessageCallback) {
    return this.safeSubscribe(`/topic/chat.room.${roomId}`, callback);
  }

  /**
   * Sends a chat message to /app/chat.sendMessage.
   */
  public sendChatMessage(messageDto: { roomId: string; sender: string; content: string; type: string }): void {
    if (!this.isConnected || !this.client.connected) {
      console.warn('[STOMP] Chat message queued/dropped: Backend offline');
      return;
    }
    try {
      this.client.publish({
        destination: '/app/chat.sendMessage',
        body: JSON.stringify(messageDto),
      });
    } catch (err) {
      console.warn('[STOMP] Failed to publish chat message:', err);
    }
  }

  /**
   * Registers a listener callback for WebSocket connection status changes.
   */
  public onConnectionChange(listener: (connected: boolean) => void): () => void {
    this.connectionListeners.push(listener);
    listener(this.isConnected);
    return () => {
      this.connectionListeners = this.connectionListeners.filter((l) => l !== listener);
    };
  }

  private notifyConnectionState(connected: boolean): void {
    this.connectionListeners.forEach((listener) => listener(connected));
  }
}

export const stompClient = new ArchiveStompClient();
