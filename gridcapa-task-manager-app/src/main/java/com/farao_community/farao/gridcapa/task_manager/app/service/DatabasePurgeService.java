/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app.service;

import com.farao_community.farao.gridcapa.task_manager.app.repository.ProcessEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * @author Jean-Pierre Arnould {@literal <jean-pierre.arnould at rte-france.com>}
 */
@EnableScheduling
@Service
public class DatabasePurgeService {

    @Value("${purge-task-events.nb-days}")
    private String nbDays;

    private static final Logger LOGGER = LoggerFactory.getLogger(DatabasePurgeService.class);

    private final ProcessEventRepository processEventRepository;

    public DatabasePurgeService(final ProcessEventRepository processEventRepository) {
        this.processEventRepository = processEventRepository;
    }

    @Scheduled(cron = "${purge-task-events.cron}")
    @Transactional
    public void scheduledDatabaseTaskEventsPurge() {
        processEventRepository.deleteWhenOlderThan(OffsetDateTime.now().minusDays(Long.parseLong(nbDays)));
        LOGGER.debug("Task events that are more than {} days old have been deleted from database ", nbDays);
    }
}
