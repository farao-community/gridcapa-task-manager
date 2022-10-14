/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app;

import com.farao_community.farao.gridcapa.task_manager.app.entities.ProcessFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
@Repository
public interface ProcessFileRepository extends JpaRepository<ProcessFile, UUID> {

    @Query("SELECT process_file FROM ProcessFile process_file LEFT JOIN FETCH process_file.tasks  WHERE process_file.fileObjectKey = :fileObjectKey")
    Optional<ProcessFile> findByFileObjectKey(@Param("fileObjectKey") String fileObjectKey);

    @Query("SELECT process_file FROM ProcessFile process_file LEFT JOIN FETCH process_file.tasks " +
        "WHERE process_file.startingAvailabilityDate = :startingAvailabilityDate AND process_file.fileType = :fileType AND process_file.fileGroup = :fileGroup")
    Optional<ProcessFile> findByStartingAvailabilityDateAndFileTypeAndGroup(@Param("startingAvailabilityDate") OffsetDateTime startingAvailabilityDate,
                                                                            @Param("fileType") String fileType,
                                                                            @Param("fileGroup") String fileGroup);
}
