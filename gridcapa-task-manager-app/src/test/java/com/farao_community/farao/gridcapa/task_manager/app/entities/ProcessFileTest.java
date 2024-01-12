/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app.entities;

import com.farao_community.farao.gridcapa.task_manager.api.ProcessFileDto;
import com.farao_community.farao.gridcapa.task_manager.api.ProcessFileStatus;
import com.farao_community.farao.gridcapa.task_manager.app.service.TaskDtoBuilderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
@SpringBootTest
class ProcessFileTest {

    @Autowired
    private TaskDtoBuilderService taskDtoBuilderService;

    @Test
    void testConstructorFromEntity() {
        ProcessFile processFile = new ProcessFile(
                "cgm-name",
                "input",
                "CGM",
                OffsetDateTime.parse("2021-10-11T00:00Z"),
                OffsetDateTime.parse("2021-10-12T00:00Z"),
                OffsetDateTime.parse("2021-10-11T10:18Z"));

        ProcessFileDto processFileDto = taskDtoBuilderService.createDtoFromEntity(processFile);
        assertEquals("CGM", processFileDto.getFileType());
        assertEquals("cgm-name", processFileDto.getFilename());
        assertEquals("cgm-name", processFileDto.getFilename());
        assertEquals(ProcessFileStatus.VALIDATED, processFileDto.getProcessFileStatus());
    }

    @Test
    void compareToTest() {
        ProcessFile processFile1 = new ProcessFile(
                "cgm-name",
                "input",
                "CGM",
                OffsetDateTime.parse("2021-10-11T00:00Z"),
                OffsetDateTime.parse("2021-10-12T00:00Z"),
                OffsetDateTime.parse("2021-10-11T10:18Z"));
        ProcessFile processFile2 = new ProcessFile(
                "cgm-name2",
                "input",
                "CGM",
                OffsetDateTime.parse("2021-10-11T00:00Z"),
                OffsetDateTime.parse("2021-10-12T00:00Z"),
                OffsetDateTime.parse("2021-10-11T10:30Z"));

        assertEquals(0, processFile1.compareTo(processFile2));
        ProcessFile processFile3 = new ProcessFile(
                "cgm-name3",
                "input",
                "CGM",
                OffsetDateTime.parse("2021-10-12T00:00Z"),
                OffsetDateTime.parse("2021-10-13T00:00Z"),
                OffsetDateTime.parse("2021-10-11T11:18Z"));
        assertEquals(-1, processFile1.compareTo(processFile3));
    }
}
