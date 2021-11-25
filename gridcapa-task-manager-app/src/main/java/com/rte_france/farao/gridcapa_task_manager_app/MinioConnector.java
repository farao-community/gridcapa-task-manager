/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.rte_france.farao.gridcapa_task_manager_app;

import io.minio.MinioClient;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.MessageListenerContainer;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

/**
 * @author Sebastien Murgey {@literal <sebastien.murgey at rte-france.com>}
 */
@Component
public class MinioConnector {
    private final TaskManagerConfigurationProperties taskManagerConfigurationProperties;

    public MinioConnector(TaskManagerConfigurationProperties taskManagerConfigurationProperties) {
        this.taskManagerConfigurationProperties = taskManagerConfigurationProperties;
    }

    @Bean
    public MinioClient generateMinioClient() {
        TaskManagerConfigurationProperties.MinIoProperties.ConnectionProperties connectionProperties = taskManagerConfigurationProperties.getMinio().getConnect();
        return MinioClient.builder().endpoint(connectionProperties.getUrl()).credentials(connectionProperties.getAccessKey(), connectionProperties.getSecretKey()).build();
    }

    @Bean
    public FanoutExchange minioEventNotificationExchange() {
        return ExchangeBuilder.fanoutExchange(taskManagerConfigurationProperties.getMinio().getNotification().getExchange()).build();
    }

    @Bean
    public Queue minioEventNotificationQueue() {
        return QueueBuilder.durable(taskManagerConfigurationProperties.getMinio().getNotification().getQueue()).build();
    }

    @Bean
    public Binding minioEventNotificationBinding(Queue minioEventNotificationQueue, FanoutExchange minioEventNotificationExchange) {
        return BindingBuilder.bind(minioEventNotificationQueue).to(minioEventNotificationExchange);
    }

    @Bean
    public MessageListenerContainer messageListenerContainer(ConnectionFactory connectionFactory, Queue minioEventNotificationQueue, MinioNotificationsListener listener) {
        SimpleMessageListenerContainer simpleMessageListenerContainer = new SimpleMessageListenerContainer();
        simpleMessageListenerContainer.setConnectionFactory(connectionFactory);
        simpleMessageListenerContainer.setQueues(minioEventNotificationQueue);
        simpleMessageListenerContainer.setMessageListener(listener);
        return simpleMessageListenerContainer;
    }
}
