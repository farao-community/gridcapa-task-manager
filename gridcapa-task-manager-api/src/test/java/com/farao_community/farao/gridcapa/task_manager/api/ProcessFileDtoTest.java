/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
class ProcessFileDtoTest {

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
