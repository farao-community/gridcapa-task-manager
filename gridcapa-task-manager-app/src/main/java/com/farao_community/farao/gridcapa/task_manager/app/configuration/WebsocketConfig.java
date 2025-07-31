package com.farao_community.farao.gridcapa.task_manager.app.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebsocketConfig implements WebSocketMessageBrokerConfigurer {

    @Value("${stomp.allowed-origin}")
    private String[] allowedOrigin;

    @Value("${stomp.heartbeat-client}")
    private long heartbeatClient;

    @Value("${stomp.heartbeat-server}")
    private long heartbeatServer;

    @Value("${stomp.starting-ws-endpoint}")
    private String startingEndpoint;

    @Value("${stomp.notify}")
    private String notify;

    @Value("${stomp.receive-request}")
    private String receiver;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        final ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(1);
        taskScheduler.setThreadNamePrefix("ws-heartbeat-thread-");
        taskScheduler.initialize();

        // These are endpoints the client can subscribes to.
        config.enableSimpleBroker(notify)
                .setHeartbeatValue(new long[]{heartbeatServer, heartbeatClient})
                .setTaskScheduler(taskScheduler);
        config.setApplicationDestinationPrefixes(receiver);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint(startingEndpoint).setAllowedOriginPatterns(allowedOrigin);
    }

    public String getNotify() {
        return notify;
    }
}
