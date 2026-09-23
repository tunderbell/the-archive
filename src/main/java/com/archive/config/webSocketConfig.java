package com.archive.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * ============================================================================
 * CLASS: WebSocketConfig
 * ============================================================================
 * WHAT IT DOES:
 * Establishes and configures the Spring STOMP-over-WebSocket message broker routing
 * infrastructure for The Archive.
 *
 * WHY IT IS USED:
 * Provides the persistent, bidirectional communication pipeline that drives:
 * 1. Multi-user workspace chat messaging.
 * 2. Real-time background Harvester/Scraper telemetry and download progression.
 * 3. Live browser terminal emulation (Xterm.js) and command bar feedback.
 *
 * SYNTAX BREAKDOWN:
 * - @Configuration: Informs the Spring IOC container that this class provides bean
 *   definitions and component configurations.
 * - @EnableWebSocketMessageBroker: Enables WebSocket message handling, backed by a
 *   messaging broker. This activates Spring's internal routing between client sessions
 *   and @MessageMapping methods.
 * - implements WebSocketMessageBrokerConfigurer: Interface providing callback methods
 *   to customize WebSocket routing, destination prefixes, and endpoints.
 * ============================================================================
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * Configures the message broker options:
     * - In-memory broker destination prefixes for outbound messages to clients.
     * - Application destination prefixes for inbound messages from clients to controllers.
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Simple in-memory message broker carries messages to clients subscribed to
        // destinations prefixed with "/topic" (pub/sub broadcast) or "/queue" (point-to-point)
        config.enableSimpleBroker("/topic", "/queue");

        // Designates the "/app" prefix for messages destined for @MessageMapping handler
        // methods in Spring controllers (e.g. client sends to "/app/chat.sendMessage")
        config.setApplicationDestinationPrefixes("/app");
    }

    /**
     * Registers STOMP endpoints mapping each to a specific URL for the WebSocket handshake.
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Register the "/ws" handshake endpoint.
        // setAllowedOriginPatterns("*") enables connections from local development frontends (e.g. React on :3000).
        // withSockJS() enables SockJS fallback options so alternate transports (like HTTP long-polling)
        // are used if the client's browser or network proxy blocks native WebSocket connections.
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }
}
