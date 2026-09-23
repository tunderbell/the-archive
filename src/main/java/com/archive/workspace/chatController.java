package com.archive.workspace;

import com.archive.workspace.model.ChatMessage;
import com.archive.workspace.model.ChatMessageRepository;
import com.archive.workspace.model.MessageType;
import com.archive.workspace.service.WorkspaceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * ============================================================================
 * CLASS: ChatController
 * ============================================================================
 * WHAT IT DOES:
 * Serves as the dual-protocol gateway for chat communication in The Archive:
 * 1. Handles high-speed, real-time STOMP frames over WebSockets (@MessageMapping).
 * 2. Exposes standard RESTful HTTP endpoints for fetching conversation history (@GetMapping).
 *
 * WHY IT IS USED:
 * Bridges browser WebSocket sessions with database persistence and pub/sub routing.
 * When a user types a message or enters a room, this controller processes the event,
 * commits it to the database, and fans out the update to all active workspace participants.
 *
 * SYNTAX BREAKDOWN:
 * - @RestController: Convenience annotation combining @Controller and @ResponseBody,
 *   marking HTTP handler methods to return serialized JSON responses.
 * - @MessageMapping("/chat.sendMessage"): Intercepts STOMP messages whose destination
 *   matches "/app/chat.sendMessage" (combining application prefix "/app" from WebSocketConfig).
 * - @SendTo("/topic/workspace.chat"): Takes the object returned by the handler method,
 *   serializes it to JSON, and broadcasts it to all subscribers of "/topic/workspace.chat".
 * - @Payload: Tells Spring to deserialize the incoming STOMP frame body into the parameter.
 * ============================================================================
 */
@RestController
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatMessageRepository chatMessageRepository;
    private final WorkspaceService workspaceService;

    public ChatController(ChatMessageRepository chatMessageRepository, WorkspaceService workspaceService) {
        this.chatMessageRepository = chatMessageRepository;
        this.workspaceService = workspaceService;
    }

    /**
     * Intercepts incoming user chat messages, persists them to the active database,
     * and broadcasts the saved message to all workspace subscribers.
     *
     * @param chatMessage Deserialized message payload sent from the client.
     * @return The persisted ChatMessage entity with its assigned UUID and timestamp.
     */
    @MessageMapping("/chat.sendMessage")
    @SendTo("/topic/workspace.chat")
    public ChatMessage sendMessage(@Payload ChatMessage chatMessage) {
        log.info("Received chat message from [{}] in channel [{}]: {}",
                chatMessage.getSender(), chatMessage.getChannel(), chatMessage.getContent());

        // Ensure default type is CHAT if omitted
        if (chatMessage.getMessageType() == null) {
            chatMessage.setMessageType(MessageType.CHAT);
        }

        // Save to database so conversation history is durable across reboots
        return chatMessageRepository.save(chatMessage);
    }

    /**
     * Handles presence notification when a user opens a workspace channel.
     *
     * @param chatMessage Presence payload containing sender name and channel.
     * @return Broadcasted JOIN announcement.
     */
    @MessageMapping("/chat.addUser")
    @SendTo("/topic/workspace.chat")
    public ChatMessage addUser(@Payload ChatMessage chatMessage) {
        log.info("User [{}] connected to workspace channel [{}]",
                chatMessage.getSender(), chatMessage.getChannel());

        chatMessage.setMessageType(MessageType.JOIN);
        chatMessage.setContent(chatMessage.getSender() + " has entered the war room.");
        return chatMessageRepository.save(chatMessage);
    }

    /**
     * REST endpoint allowing clients to load historical messages when joining a channel.
     * Example: GET /api/chat/history?channel=general&limit=50
     *
     * @param channel The channel name (default: "general").
     * @param limit   Max number of recent messages to return (default: 50).
     * @return Chronologically ordered list of previous messages.
     */
    @GetMapping("/api/chat/history")
    public List<ChatMessage> getChatHistory(
            @RequestParam(defaultValue = "general") String channel,
            @RequestParam(defaultValue = "50") int limit) {
        return workspaceService.getChatHistory(channel, limit);
    }
}
