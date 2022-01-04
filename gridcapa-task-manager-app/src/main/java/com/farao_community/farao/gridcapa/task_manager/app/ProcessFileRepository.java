/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app;

import com.farao_community.farao.gridcapa.task_manager.app.entities.ProcessFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
@Repository
public interface ProcessFileRepository extends JpaRepository<ProcessFile, UUID> {

    Optional<ProcessFile> findByFileObjectKey(String fileObjectKey);

    Optional<ProcessFile> findByStartingAvailabilityDateAndFileType(OffsetDateTime startingAvailabilityDate, String fileType);
}
