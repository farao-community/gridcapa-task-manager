package com.farao_community.farao.gridcapa.task_manager.app.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebsocketConfig implements WebSocketMessageBrokerConfigurer {

    @Value("${stomp.allowed-origin}")
    private String[] allowedOrigin;

    @Value("${stomp.starting-ws-endpoint}")
    private String startingEndpoint;

    @Value("${stomp.notify}")
    private String notify;

    @Value("${stomp.receive-request}")
    private String receiver;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // These are endpoints the client can subscribes to.
        config.enableSimpleBroker(notify);
        config.setApplicationDestinationPrefixes(receiver);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint(startingEndpoint).setAllowedOriginPatterns(allowedOrigin).withSockJS();
    }

}
