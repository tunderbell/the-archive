package com.archive.workspace.service;

import com.archive.workspace.model.ChatMessage;
import com.archive.workspace.model.ChatMessageRepository;
import com.archive.workspace.model.WorkspaceActivity;
import com.archive.workspace.model.WorkspaceActivityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * ============================================================================
 * CLASS: WorkspaceService
 * ============================================================================
 * WHAT IT DOES:
 * Encapsulates the core business logic for multi-user collaboration, real-time message
 * broadcasting, and audit logging in The Archive's shared workspace.
 *
 * WHY IT IS USED:
 * Separates protocol handling (WebSocket and REST controllers) from data persistence
 * and cross-system notifications. When other subsystems (such as the Scraper Engine)
 * need to announce completions or log library updates, they call this service.
 *
 * SYNTAX BREAKDOWN:
 * - @Service: Marks this class as a Spring business service bean.
 * - @Transactional: Wraps operations in database transactions.
 * - SimpMessagingTemplate: Spring's high-level helper class for programmatically sending
 *   STOMP messages to WebSocket subscribers from any thread or background task.
 * ============================================================================
 */
@Service
@Transactional
public class WorkspaceService {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceService.class);

    private final ChatMessageRepository chatMessageRepository;
    private final WorkspaceActivityRepository activityRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public WorkspaceService(
            ChatMessageRepository chatMessageRepository,
            WorkspaceActivityRepository activityRepository,
            SimpMessagingTemplate messagingTemplate) {
        this.chatMessageRepository = chatMessageRepository;
        this.activityRepository = activityRepository;
        this.messagingTemplate = messagingTemplate;
    }

    // --- Chat Operations ---

    /**
     * Persists an incoming chat message and pushes it out over the STOMP broker
     * to all connected workspace clients.
     *
     * @param message The ChatMessage entity to persist and broadcast.
     * @return The saved ChatMessage with its generated UUID and timestamp.
     */
    public ChatMessage processAndBroadcastChat(ChatMessage message) {
        ChatMessage saved = chatMessageRepository.save(message);

        // Broadcast to all subscribers of the general workspace chat topic
        messagingTemplate.convertAndSend("/topic/workspace.chat", saved);
        log.info("Broadcasted chat message from [{}] to channel [{}]", saved.getSender(), saved.getChannel());
        return saved;
    }

    /**
     * Emits a system notification into the workspace chat stream.
     * Invoked by background workers (e.g. Scraper Harvester) to notify users in real time.
     *
     * @param text    The message description.
     * @param channel Target channel name.
     * @return The persisted system alert ChatMessage.
     */
    public ChatMessage broadcastSystemAlert(String text, String channel) {
        ChatMessage alert = ChatMessage.createSystemAlert(text, channel);
        return processAndBroadcastChat(alert);
    }

    /**
     * Retrieves recent chat history for a channel in chronological order (oldest to newest).
     */
    @Transactional(readOnly = true)
    public List<ChatMessage> getChatHistory(String channel, int limit) {
        List<ChatMessage> messages = chatMessageRepository
                .findByChannelOrderByTimestampDesc(channel, PageRequest.of(0, limit));

        // Queries fetch newest first (for indexing efficiency); reverse to display oldest to newest
        Collections.reverse(messages);
        return messages;
    }

    // --- Workspace Activity Logging Operations ---

    /**
     * Records a significant workspace event and broadcasts it to the real-time activity feed.
     *
     * @param actor       User or subsystem that triggered the action.
     * @param action      Action category code (e.g. "SERIES_ARCHIVED").
     * @param details     Descriptive text.
     * @param mediaDomain Media type ("MANGA", "ANIME", "MUSIC", "VIDEO_GAME").
     * @param mediaId     Optional UUID of the affected entity.
     * @return The persisted WorkspaceActivity record.
     */
    public WorkspaceActivity recordActivity(String actor, String action, String details, String mediaDomain, UUID mediaId) {
        WorkspaceActivity activity = new WorkspaceActivity(actor, action, details, mediaDomain, mediaId);
        WorkspaceActivity saved = activityRepository.save(activity);

        // Push real-time event to dashboard activity feed widget subscribers
        messagingTemplate.convertAndSend("/topic/workspace.activity", saved);
        log.info("Recorded workspace activity: [{}] performed [{}] - {}", actor, action, details);
        return saved;
    }

    /**
     * Retrieves the most recent workspace activities across all domains.
     */
    @Transactional(readOnly = true)
    public List<WorkspaceActivity> getRecentActivities(int limit) {
        return activityRepository.findAllByOrderByTimestampDesc(PageRequest.of(0, limit));
    }

    /**
     * Retrieves recent workspace activities filtered by a specific media domain.
     */
    @Transactional(readOnly = true)
    public List<WorkspaceActivity> getActivitiesByDomain(String mediaDomain, int limit) {
        return activityRepository.findByMediaDomainOrderByTimestampDesc(mediaDomain, PageRequest.of(0, limit));
    }
}
