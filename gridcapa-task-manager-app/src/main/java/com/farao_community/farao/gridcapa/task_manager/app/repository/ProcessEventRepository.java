/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app.repository;

import com.farao_community.farao.gridcapa.task_manager.app.entities.ProcessEvent;
import com.farao_community.farao.gridcapa.task_manager.app.entities.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * @author Daniel THIRION {@literal <daniel.thirion at rte-france.com>}
 */
@Repository
public interface ProcessEventRepository extends JpaRepository<ProcessEvent, UUID> {

    @Modifying
    @Transactional
    @Query("DELETE FROM ProcessEvent pe WHERE pe.task = :task")
    void deleteByTask(@Param("task") Task task);

    @Modifying
    @Transactional
    @Query("DELETE FROM ProcessEvent pe WHERE pe.timestamp < :threshold")
    void deleteWhenOlderThan(@Param("threshold") OffsetDateTime threshold);
}
