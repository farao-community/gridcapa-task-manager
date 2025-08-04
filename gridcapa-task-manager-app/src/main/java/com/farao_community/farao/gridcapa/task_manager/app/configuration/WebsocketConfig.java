/*
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
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

    private final ThreadPoolTaskScheduler taskScheduler;

    public WebsocketConfig(final ThreadPoolTaskScheduler taskScheduler) {
        this.taskScheduler = taskScheduler;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
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
