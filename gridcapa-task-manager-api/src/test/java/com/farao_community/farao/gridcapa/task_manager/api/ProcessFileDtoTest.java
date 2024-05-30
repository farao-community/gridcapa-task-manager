/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.api;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
class ProcessFileDtoTest {

    @Test
    void testConstructorEmptyProcessFile() {
        ProcessFileDto processFileDto = ProcessFileDto.emptyProcessFile("CGM");
        assertEquals("CGM", processFileDto.getFileType());
        assertNull(processFileDto.getFilePath());
        assertNull(processFileDto.getFilename());
        assertNull(processFileDto.getLastModificationDate());
        assertEquals(ProcessFileStatus.NOT_PRESENT, processFileDto.getProcessFileStatus());
    }

    @Test
    void testEqualsAndHashCode() {
        OffsetDateTime now = OffsetDateTime.now();
        ProcessFileDto referenceDto = new ProcessFileDto("path/to/filename", "FILETYPE", ProcessFileStatus.VALIDATED, "filename", now);
        ProcessFileDto equalDto = new ProcessFileDto("path/to/filename", "FILETYPE", ProcessFileStatus.VALIDATED, "filename", now);
        ProcessFileDto diffFilePathDto = new ProcessFileDto("other/path/to/filename", "FILETYPE", ProcessFileStatus.VALIDATED, "filename", now);
        ProcessFileDto diffFileTypeDto = new ProcessFileDto("path/to/filename", "DIFFERENT", ProcessFileStatus.VALIDATED, "filename", now);
        ProcessFileDto diffStatusDto = new ProcessFileDto("path/to/filename", "FILETYPE", ProcessFileStatus.NOT_PRESENT, "filename", now);
        ProcessFileDto diffFilenameDto = new ProcessFileDto("path/to/filename", "FILETYPE", ProcessFileStatus.VALIDATED, "other", now);
        ProcessFileDto diffDateDto = new ProcessFileDto("path/to/filename", "FILETYPE", ProcessFileStatus.VALIDATED, "filename", now.minusMinutes(1));

        assertFalse(referenceDto.equals(null)); // do not use assertNotEquals, which does not call equals method in this case
        assertNotEquals(referenceDto, new Object());
        assertEquals(referenceDto, referenceDto);
        assertEquals(equalDto, referenceDto);
        assertNotEquals(diffFilePathDto, referenceDto);
        assertNotEquals(diffFileTypeDto, referenceDto);
        assertNotEquals(diffStatusDto, referenceDto);
        assertNotEquals(diffFilenameDto, referenceDto);
        assertNotEquals(diffDateDto, referenceDto);
        assertEquals(equalDto.hashCode(), referenceDto.hashCode());
        assertNotEquals(diffFilePathDto.hashCode(), referenceDto.hashCode());
        assertNotEquals(diffFileTypeDto.hashCode(), referenceDto.hashCode());
        assertNotEquals(diffStatusDto.hashCode(), referenceDto.hashCode());
        assertNotEquals(diffFilenameDto.hashCode(), referenceDto.hashCode());
        assertNotEquals(diffDateDto.hashCode(), referenceDto.hashCode());
    }
}
