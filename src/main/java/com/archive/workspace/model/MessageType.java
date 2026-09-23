package com.archive.workspace.model;

/**
 * ============================================================================
 * ENUM: MessageType
 * ============================================================================
 * WHAT IT DOES:
 * Categorizes real-time messaging payloads into distinct functional types across
 * the workspace channel infrastructure.
 *
 * WHY IT IS USED:
 * A unified WebSocket channel delivers both human conversation and automated system
 * telemetries. The frontend UI inspects this type to apply specialized UI styles:
 * - CHAT: Standard user speech bubble with user avatar.
 * - JOIN: System presence notice when a user connects to a workspace channel.
 * - LEAVE: Presence notice when a user disconnects or closes their session.
 * - SYSTEM_ALERT: High-priority operational broadcast (e.g. Scraper completed).
 *
 * SYNTAX BREAKDOWN:
 * - enum: A specialized Java data type defining a fixed set of constants.
 * ============================================================================
 */
public enum MessageType {
    CHAT,
    JOIN,
    LEAVE,
    SYSTEM_ALERT
}
