package com.farao_community.farao.gridcapa.task_manager.app.service;

import com.farao_community.farao.gridcapa.task_manager.app.ProcessEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;


/**
 * @author arnouldjpi
 */
@EnableScheduling
@Service
public class DatabasePurgeService {

    @Value("${purge-task-events.nb-days}")
    private String nbDays;

    private static final Logger LOGGER = LoggerFactory.getLogger(EventHandler.class);

    private ProcessEventRepository processEventRepository;

    public void DatabasePurgeService(ProcessEventRepository processEventRepository) {
        this.processEventRepository = processEventRepository;
    }

    @Scheduled(cron = "${purge-task-events.cron}")
    public void scheduledDatabaseTaskEventsPurge() {
        OffsetDateTime dateTimeNow = OffsetDateTime.now();
        OffsetDateTime dateTimeReference = dateTimeNow.minusDays(Long.getLong(nbDays));
        processEventRepository.deleteByTimestampBefore(dateTimeReference);
        LOGGER.debug("Task events that are more than {} days old have been deleted from database ", nbDays);
    }
}
