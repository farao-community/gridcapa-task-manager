/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app.service;

import com.farao_community.farao.gridcapa.task_manager.app.repository.TaskRepository;
import com.farao_community.farao.gridcapa.task_manager.app.entities.ProcessEvent;
import com.farao_community.farao.gridcapa.task_manager.app.entities.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;


/**
 * @author Jean-Pierre Arnould {@literal <jean-pierre.arnould at rte-france.com>}
 */
@EnableScheduling
@Service
public class DatabasePurgeService {

    @Value("${purge-task-events.nb-days}")
    private String nbDays;

    private static final Logger LOGGER = LoggerFactory.getLogger(EventHandler.class);

    @Autowired
    private TaskRepository taskRepository;

    @Scheduled(cron = "${purge-task-events.cron}")
    @Transactional
    public void scheduledDatabaseTaskEventsPurge() {
        OffsetDateTime dateTimeNow = OffsetDateTime.now();
        OffsetDateTime dateTimeReference = dateTimeNow.minusDays(Long.valueOf(nbDays));

        List<Task> listTasksToSave = new ArrayList<>();
        for (Task task : taskRepository.findAllWithSomeProcessEvent()) {
            List<ProcessEvent> listProcessEventsToRemove = task.getProcessEvents().stream()
                    .filter(processEvent -> processEvent.getTimestamp().isBefore(dateTimeReference))
                    .collect(Collectors.toList());
            if (!listProcessEventsToRemove.isEmpty()) {
                task.getProcessEvents().removeAll(listProcessEventsToRemove);
                listTasksToSave.add(task);
            }
        }

        taskRepository.saveAll(listTasksToSave);

        LOGGER.debug("Task events that are more than {} days old have been deleted from database ", nbDays);
    }
}
