/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app;

import com.farao_community.farao.gridcapa.task_manager.app.entities.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
@Repository
public interface TaskRepository extends JpaRepository<Task, UUID> {

    @Query("SELECT task FROM Task task LEFT JOIN FETCH task.processFiles " +
            "LEFT JOIN FETCH task.processEvents " +
            "WHERE task.id = :id")
    Optional<Task> findByIdWithProcessFiles(@Param("id") UUID id);

    @Query("SELECT task FROM Task task LEFT JOIN FETCH task.processFiles " +
            "LEFT JOIN FETCH task.processEvents " +
            "WHERE task.timestamp = :timestamp")
    Optional<Task> findByTimestamp(@Param("timestamp") OffsetDateTime timestamp);

    @Query("SELECT task FROM Task task JOIN FETCH task.processFiles " +
            "LEFT JOIN FETCH task.processEvents " +
        "WHERE task.timestamp >= :startingTimestamp AND task.timestamp < :endingTimestamp")
    Set<Task> findAllByTimestampBetween(@Param("startingTimestamp") OffsetDateTime startingTimestamp,
                                        @Param("endingTimestamp") OffsetDateTime endingTimestamp);

    @Query("SELECT task FROM Task task JOIN FETCH task.processFiles " +
            "LEFT JOIN FETCH task.processEvents " +
            "WHERE task.status = com.farao_community.farao.gridcapa.task_manager.api.TaskStatus.RUNNING " +
            "OR task.status = com.farao_community.farao.gridcapa.task_manager.api.TaskStatus.PENDING")
    Set<Task> findAllRunningAndPending();
}
