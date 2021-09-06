/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 */
package com.farao_community.farao.gridcapa.task_manager;

import io.minio.MinioClient;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.MessageListenerContainer;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.UUID;

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
        TaskManagerConfigurationProperties.ConnectionProperties connectionProperties = taskManagerConfigurationProperties.getConnect();
        return MinioClient.builder().endpoint(connectionProperties.getUrl()).credentials(connectionProperties.getAccessKey(), connectionProperties.getSecretKey()).build();
    }

    @Bean
    public FanoutExchange minioEventNotificationExchange() {
        return ExchangeBuilder.fanoutExchange(taskManagerConfigurationProperties.getNotification().getExchange()).build();
    }

    @Bean
    public Queue minioEventNotificationQueue() {
        return QueueBuilder.durable(taskManagerConfigurationProperties.getNotification().getQueuePrefix() + "." + UUID.randomUUID()).build();
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
