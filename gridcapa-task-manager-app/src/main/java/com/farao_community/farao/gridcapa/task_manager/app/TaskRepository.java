/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app;

import com.farao_community.farao.gridcapa.task_manager.app.entities.Task;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
public interface TaskRepository extends NaturalRepository<Task, OffsetDateTime> {

    @Query("SELECT task FROM Task task JOIN FETCH task.processFiles process_file WHERE process_file.id = :processFileId")
    Set<Task> findTasksByProcessFileId(@Param("processFileId") UUID processFileId);

    @Query("SELECT task FROM Task task WHERE task.timestamp >= :startingTimestamp AND task.timestamp <= :endingTimestamp")
    Set<Task> findTasksByStartingAndEndingTimestamp(@Param("startingTimestamp") OffsetDateTime startingTimestamp, @Param("endingTimestamp") OffsetDateTime endingTimestamp);

    @Query("SELECT task FROM Task task JOIN FETCH task.processFiles WHERE task.timestamp >= :startingTimestamp AND task.timestamp < :endingTimestamp")
    Set<Task> findTasksByStartingAndEndingTimestampEager(@Param("startingTimestamp") OffsetDateTime startingTimestamp, @Param("endingTimestamp") OffsetDateTime endingTimestamp);

    @Query("SELECT task FROM Task task LEFT JOIN FETCH task.processFiles WHERE task.timestamp = :timestamp")
    Optional<Task> findTaskByTimestampEagerLeft(@Param("timestamp") OffsetDateTime timestamp);

    @Query("SELECT task FROM Task task JOIN FETCH task.processFiles WHERE task.timestamp = :timestamp")
    Optional<Task> findTaskByTimestampEager(@Param("timestamp") OffsetDateTime timestamp);
}
