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
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatMessageRepository chatMessageRepository;
    private final WorkspaceService workspaceService;
    private final SimpMessagingTemplate messagingTemplate;

    public ChatController(
            ChatMessageRepository chatMessageRepository,
            WorkspaceService workspaceService,
            SimpMessagingTemplate messagingTemplate) {
        this.chatMessageRepository = chatMessageRepository;
        this.workspaceService = workspaceService;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Intercepts incoming user chat messages, persists them to the active database,
     * and broadcasts the saved message to both general and room-specific topics.
     */
    @MessageMapping("/chat.sendMessage")
    public ChatMessage sendMessage(@Payload ChatMessage chatMessage) {
        log.info("Received chat message from [{}] in channel [{}]: {}",
                chatMessage.getSender(), chatMessage.getChannel(), chatMessage.getContent());

        if (chatMessage.getMessageType() == null) {
            chatMessage.setMessageType(MessageType.CHAT);
        }

        ChatMessage saved = chatMessageRepository.save(chatMessage);
        messagingTemplate.convertAndSend("/topic/workspace.chat", saved);
        messagingTemplate.convertAndSend("/topic/chat.room." + saved.getChannel(), saved);
        return saved;
    }

    /**
     * Handles presence notification when a user opens a workspace channel.
     */
    @MessageMapping("/chat.addUser")
    public ChatMessage addUser(@Payload ChatMessage chatMessage) {
        log.info("User [{}] connected to workspace channel [{}]",
                chatMessage.getSender(), chatMessage.getChannel());

        chatMessage.setMessageType(MessageType.JOIN);
        chatMessage.setContent(chatMessage.getSender() + " has entered the room.");
        ChatMessage saved = chatMessageRepository.save(chatMessage);
        messagingTemplate.convertAndSend("/topic/workspace.chat", saved);
        messagingTemplate.convertAndSend("/topic/chat.room." + saved.getChannel(), saved);
        return saved;
    }

    /**
     * REST endpoint allowing clients to load historical messages by query param.
     * Example: GET /api/chat/history?channel=general&limit=50
     */
    @GetMapping("/api/chat/history")
    public List<ChatMessage> getChatHistory(
            @RequestParam(defaultValue = "general") String channel,
            @RequestParam(defaultValue = "50") int limit) {
        return workspaceService.getChatHistory(channel, limit);
    }

    /**
     * REST endpoint allowing clients to load historical messages by path variable.
     * Example: GET /api/chat/history/global?limit=50
     */
    @GetMapping("/api/chat/history/{channel}")
    public List<ChatMessage> getChatHistoryByPath(
            @PathVariable String channel,
            @RequestParam(defaultValue = "50") int limit) {
        return workspaceService.getChatHistory(channel, limit);
    }
}
