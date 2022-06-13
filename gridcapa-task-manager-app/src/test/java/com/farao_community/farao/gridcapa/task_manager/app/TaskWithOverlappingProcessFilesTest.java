/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app;

import com.farao_community.farao.gridcapa.task_manager.app.entities.ProcessFile;
import com.farao_community.farao.gridcapa.task_manager.app.entities.Task;
import com.farao_community.farao.minio_adapter.starter.MinioAdapterConstants;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Mohamed Ben Rejeb {@literal <mohamed.ben-rejeb at rte-france.com>}
 */
@SpringBootTest
class TaskWithOverlappingProcessFilesTest {

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private ProcessFileRepository processFileRepository;

    private final OffsetDateTime interval1Start = OffsetDateTime.of(2020, 1, 1, 0, 30, 0, 0, ZoneOffset.UTC);
    private final OffsetDateTime interval1End = interval1Start.plusMonths(12);

    private final OffsetDateTime interval2Start = OffsetDateTime.of(2020, 6, 1, 0, 30, 0, 0, ZoneOffset.UTC);
    private final OffsetDateTime interval2End = interval2Start.plusMonths(12);

    private final OffsetDateTime taskTimeStampInInterval1 = OffsetDateTime.of(2020, 2, 1, 0, 30, 0, 0, ZoneOffset.UTC);
    private final OffsetDateTime taskTimeStampInInterval2 = OffsetDateTime.of(2021, 2, 1, 0, 30, 0, 0, ZoneOffset.UTC);
    private final OffsetDateTime taskTimeStampInBothIntervals = OffsetDateTime.of(2020, 8, 1, 0, 30, 0, 0, ZoneOffset.UTC);

    @Test
    void checkCorrectFileIsUsedWhenIntervalsOverlap() {
        ProcessFile fileInterval1 = new ProcessFile(
            "/File-1",
            MinioAdapterConstants.DEFAULT_GRIDCAPA_INPUT_GROUP_METADATA_VALUE,
            "File",
            interval1Start,
            interval1End,
            "http://File-1",
            OffsetDateTime.now());

        ProcessFile fileInterval2 = new ProcessFile(
            "/File-2",
            MinioAdapterConstants.DEFAULT_GRIDCAPA_INPUT_GROUP_METADATA_VALUE,
            "File",
            interval2Start,
            interval2End,
            "http://File-2",
            OffsetDateTime.now().plusMinutes(1));

        Task taskInterval1 = new Task(taskTimeStampInInterval1);
        Task taskInterval2 = new Task(taskTimeStampInInterval2);
        Task taskHavingFileWithOverlappingIntervals = new Task(taskTimeStampInBothIntervals);

        processFileRepository.save(fileInterval1);
        processFileRepository.save(fileInterval2);

        taskInterval1.addProcessFile(fileInterval1);
        taskInterval1.addProcessFile(fileInterval2);
        taskRepository.save(taskInterval1);

        taskInterval2.addProcessFile(fileInterval1);
        taskInterval2.addProcessFile(fileInterval2);
        taskRepository.save(taskInterval2);

        taskHavingFileWithOverlappingIntervals.addProcessFile(fileInterval1);
        taskHavingFileWithOverlappingIntervals.addProcessFile(fileInterval2);
        taskRepository.save(taskHavingFileWithOverlappingIntervals);

        assertEquals("File-1", taskInterval1.getInput("File", taskInterval1.getTimestamp()).get().getFilename());
        assertEquals("File-2", taskInterval2.getInput("File", taskInterval2.getTimestamp()).get().getFilename());
        assertEquals("File-2", taskHavingFileWithOverlappingIntervals.getInput("File", taskHavingFileWithOverlappingIntervals.getTimestamp()).get().getFilename());

        taskInterval1.removeProcessFile(fileInterval2);
        taskInterval2.removeProcessFile(fileInterval2);
        taskHavingFileWithOverlappingIntervals.removeProcessFile(fileInterval2);
        taskRepository.saveAndFlush(taskInterval1);
        taskRepository.saveAndFlush(taskInterval2);
        taskRepository.saveAndFlush(taskHavingFileWithOverlappingIntervals);
        processFileRepository.delete(fileInterval2);
        assertEquals("File-1", taskHavingFileWithOverlappingIntervals.getInput("File", taskHavingFileWithOverlappingIntervals.getTimestamp()).get().getFilename());

    }

}
