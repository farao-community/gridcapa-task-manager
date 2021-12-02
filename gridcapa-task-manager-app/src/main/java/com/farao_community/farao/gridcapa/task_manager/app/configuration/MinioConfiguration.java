package com.farao_community.farao.gridcapa.task_manager.app.configuration;

import io.minio.MinioClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
@Configuration
public class MinioConfiguration {

    private final TaskManagerConfigurationProperties taskManagerConfigurationProperties;

    public MinioConfiguration(TaskManagerConfigurationProperties taskManagerConfigurationProperties) {
        this.taskManagerConfigurationProperties = taskManagerConfigurationProperties;
    }

    @Bean
    public MinioClient generateMinioClient() {
        TaskManagerConfigurationProperties.MinIoProperties.ConnectionProperties connectionProperties = taskManagerConfigurationProperties.getMinio().getConnect();
        return MinioClient.builder().endpoint(connectionProperties.getUrl()).credentials(connectionProperties.getAccessKey(), connectionProperties.getSecretKey()).build();
    }
}
