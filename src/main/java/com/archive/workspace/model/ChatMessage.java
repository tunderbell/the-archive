package com.archive.workspace.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * ============================================================================
 * ENTITY: ChatMessage
 * ============================================================================
 * WHAT IT DOES:
 * Represents a persistent chat message or system announcement inside a workspace channel.
 *
 * WHY IT IS USED:
 * Real-time WebSockets are ephemeral by default: if a client is offline or reconnects,
 * messages sent while disconnected are lost forever unless persisted to a database.
 * This entity allows The Archive to save every message so clients can request historical
 * conversation logs via REST upon entering a channel.
 *
 * SYNTAX BREAKDOWN:
 * - @Entity: Tells JPA/Hibernate that this Java class maps directly to a relational table.
 * - @Table(name = "chat_message"): Explicitly specifies the database table name.
 * - @Id & @GeneratedValue(strategy = GenerationType.UUID): Assigns an RFC 4122 random UUID
 *   as the primary key, ensuring decentralized ID uniqueness across multi-vault environments.
 * - @Enumerated(EnumType.STRING): Instructs Hibernate to store the enum's name (e.g. "CHAT")
 *   rather than its ordinal index (0, 1), ensuring backwards compatibility if enum order changes.
 * - columnDefinition = "TEXT": Bypasses the default VARCHAR(255) constraint so multi-line
 *   messages or formatted Markdown text are never truncated.
 * ============================================================================
 */
@Entity
@Table(name = "chat_message")
@Getter
@Setter
@NoArgsConstructor
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "uuid", columnDefinition = "UUID", updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false)
    private String sender;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private String channel = "general";

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false)
    private MessageType messageType = MessageType.CHAT;

    @CreationTimestamp
    @Column(name = "timestamp", updatable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime timestamp;

    /**
     * Factory helper to construct a standardized SYSTEM_ALERT message.
     * Used by background services (like ScraperService or Harvester) to notify users.
     *
     * @param text    The announcement or operational notification text.
     * @param channel The target workspace channel (defaults to "general" if null).
     * @return A populated ChatMessage instance.
     */
    public static ChatMessage createSystemAlert(String text, String channel) {
        ChatMessage msg = new ChatMessage();
        msg.setSender("SYSTEM");
        msg.setContent(text);
        msg.setChannel(channel != null && !channel.isBlank() ? channel : "general");
        msg.setMessageType(MessageType.SYSTEM_ALERT);
        return msg;
    }

    // Compatibility helpers for JSON serialization across React & Spring
    public String getRoomId() {
        return channel;
    }

    public void setRoomId(String roomId) {
        if (roomId != null && !roomId.isBlank()) {
            this.channel = roomId;
        }
    }

    public MessageType getType() {
        return messageType;
    }

    public void setType(MessageType type) {
        if (type != null) {
            this.messageType = type;
        }
    }
}
