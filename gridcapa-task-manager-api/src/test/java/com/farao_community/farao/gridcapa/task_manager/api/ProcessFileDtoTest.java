/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.api;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
class ProcessFileDtoTest {

    @Test
    void testConstructorFromEntity() {
        Task task = Mockito.mock(Task.class);
        ProcessFile processFile = new ProcessFile(task, "CGM");
        processFile.setFileUrl("cgm-url");
        processFile.setFilename("cgm-name");
        processFile.setLastModificationDate(LocalDateTime.parse("2021-10-11T10:18"));
        processFile.setProcessFileStatus(ProcessFileStatus.VALIDATED);

        ProcessFileDto processFileDto = ProcessFileDto.fromEntity(processFile);
        assertEquals("CGM", processFileDto.getFileType());
        assertEquals("cgm-url", processFileDto.getFileUrl());
        assertEquals("cgm-name", processFileDto.getFilename());
        assertEquals(LocalDateTime.parse("2021-10-11T10:18"), processFileDto.getLastModificationDate());
        assertEquals(ProcessFileStatus.VALIDATED, processFileDto.getProcessFileStatus());
    }

    @Test
    void testConstructorEmptyProcessFile() {
        ProcessFileDto processFileDto = ProcessFileDto.emptyProcessFile("CGM");
        assertEquals("CGM", processFileDto.getFileType());
        assertNull(processFileDto.getFileUrl());
        assertNull(processFileDto.getFilename());
        assertNull(processFileDto.getLastModificationDate());
        assertEquals(ProcessFileStatus.NOT_PRESENT, processFileDto.getProcessFileStatus());
    }
}
