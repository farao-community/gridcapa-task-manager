/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app;

import com.farao_community.farao.gridcapa.task_manager.app.entities.Task;
import com.farao_community.farao.gridcapa.task_manager.app.repository.ProcessFileRepository;
import com.farao_community.farao.gridcapa.task_manager.app.repository.TaskRepository;
import com.farao_community.farao.gridcapa.task_manager.app.service.MinioHandler;
import com.farao_community.farao.minio_adapter.starter.MinioAdapter;
import com.farao_community.farao.minio_adapter.starter.MinioAdapterConstants;
import io.minio.messages.Event;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Mohamed Ben Rejeb {@literal <mohamed.ben-rejeb at rte-france.com>}
 */
@SpringBootTest
class TaskWithOverlappingProcessFilesTest {

    @MockBean
    private TaskUpdateNotifier taskUpdateNotifier; // Useful to avoid AMQP connection that would fail

    @MockBean
    private MinioAdapter minioAdapter;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private MinioHandler minioHandler;

    @Autowired
    private ProcessFileRepository processFileRepository;

    @AfterEach
    void cleanDatabase() {
        taskRepository.deleteAll();
        processFileRepository.deleteAll();
    }

    private final OffsetDateTime taskTimeStampInInterval1 = OffsetDateTime.of(2020, 2, 1, 0, 30, 0, 0, ZoneOffset.UTC);
    private final OffsetDateTime taskTimeStampInInterval2 = OffsetDateTime.of(2021, 2, 1, 0, 30, 0, 0, ZoneOffset.UTC);
    private final OffsetDateTime taskTimeStampInBothIntervals = OffsetDateTime.of(2020, 8, 1, 0, 30, 0, 0, ZoneOffset.UTC);

    @Test
    void checkCorrectFileIsUsedWhenIntervalsOverlap() {
        Event eventFileInterval1 = TaskManagerTestUtil.createEvent(minioAdapter, "CSE_D2CC", MinioAdapterConstants.DEFAULT_GRIDCAPA_INPUT_GROUP_METADATA_VALUE,
            "File", "File-1",
            "2020-01-01T22:30Z/2020-12-31T22:30Z", "File-1-url");

        Event eventFileInterval2 = TaskManagerTestUtil.createEvent(minioAdapter, "CSE_D2CC", MinioAdapterConstants.DEFAULT_GRIDCAPA_INPUT_GROUP_METADATA_VALUE,
            "File", "File-2",
            "2020-06-01T22:30Z/2021-06-30T22:30Z", "File-2-url");

        minioHandler.updateTasks(eventFileInterval1);
        minioHandler.updateTasks(eventFileInterval2);

        Task taskInterval1 = taskRepository.findByTimestamp(taskTimeStampInInterval1).orElseThrow();
        Task taskInterval2 = taskRepository.findByTimestamp(taskTimeStampInInterval2).orElseThrow();
        Task taskHavingFileWithOverlappingIntervals = taskRepository.findByTimestamp(taskTimeStampInBothIntervals).orElseThrow();

        assertEquals("File-1", taskInterval1.getInput("File").get().getFilename());
        assertEquals("File-2", taskInterval2.getInput("File").get().getFilename());
        assertEquals("File-2", taskHavingFileWithOverlappingIntervals.getInput("File").get().getFilename());

        Event eventFile2Deletion = TaskManagerTestUtil.createEvent(minioAdapter, "CSE_D2CC", MinioAdapterConstants.DEFAULT_GRIDCAPA_INPUT_GROUP_METADATA_VALUE,
            "File", "File-2", "2020-06-01T22:30Z/2021-06-30T22:30Z", "File-2-url");
        minioHandler.removeProcessFile(eventFile2Deletion);
        taskHavingFileWithOverlappingIntervals = taskRepository.findByTimestamp(taskTimeStampInBothIntervals).orElseThrow();

        assertEquals("File-1", taskHavingFileWithOverlappingIntervals.getInput("File").get().getFilename());
    }
}
