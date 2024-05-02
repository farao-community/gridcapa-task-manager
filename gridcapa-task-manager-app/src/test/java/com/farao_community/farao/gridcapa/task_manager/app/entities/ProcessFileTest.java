/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app.entities;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Vincent Bochet {@literal <vincent.bochet at rte-france.com>}
 */
class ProcessFileTest {

    @Test
    void isInputTest() {
        ProcessFile processFileInput = new ProcessFile(
                "cgm-name",
                "input",
                "CGM",
                OffsetDateTime.parse("2021-10-11T00:00Z"),
                OffsetDateTime.parse("2021-10-12T00:00Z"),
                OffsetDateTime.parse("2021-10-11T10:18Z"));

        assertTrue(processFileInput.isInputFile());
    }

    @Test
    void isNotInputTest() {
        ProcessFile processFileOutput = new ProcessFile(
                "file-name",
                "output",
                "CNE",
                OffsetDateTime.parse("2021-10-11T00:00Z"),
                OffsetDateTime.parse("2021-10-12T00:00Z"),
                OffsetDateTime.parse("2021-10-11T10:18Z"));

        ProcessFile processFileArtifact = new ProcessFile(
                "file-name",
                "artifact",
                "RANDOM",
                OffsetDateTime.parse("2021-10-11T00:00Z"),
                OffsetDateTime.parse("2021-10-12T00:00Z"),
                OffsetDateTime.parse("2021-10-11T10:18Z"));

        assertFalse(processFileOutput.isInputFile());
        assertFalse(processFileArtifact.isInputFile());
    }

    @Test
    void isOutputTest() {
        ProcessFile processFileOutput = new ProcessFile(
                "file-name",
                "output",
                "CNE",
                OffsetDateTime.parse("2021-10-11T00:00Z"),
                OffsetDateTime.parse("2021-10-12T00:00Z"),
                OffsetDateTime.parse("2021-10-11T10:18Z"));

        assertTrue(processFileOutput.isOutputFile());
    }

    @Test
    void isNotOutputTest() {
        ProcessFile processFileInput = new ProcessFile(
                "cgm-name",
                "input",
                "CGM",
                OffsetDateTime.parse("2021-10-11T00:00Z"),
                OffsetDateTime.parse("2021-10-12T00:00Z"),
                OffsetDateTime.parse("2021-10-11T10:18Z"));

        ProcessFile processFileArtifact = new ProcessFile(
                "file-name",
                "artifact",
                "RANDOM",
                OffsetDateTime.parse("2021-10-11T00:00Z"),
                OffsetDateTime.parse("2021-10-12T00:00Z"),
                OffsetDateTime.parse("2021-10-11T10:18Z"));

        assertFalse(processFileArtifact.isOutputFile());
        assertFalse(processFileArtifact.isOutputFile());
    }

    @Test
    void compareToEqualFilesTest() {
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
                OffsetDateTime.parse("2021-10-11T10:18Z"));
        ProcessFile processFile3 = new ProcessFile(
                "cgm-name3",
                "input",
                "CGM",
                OffsetDateTime.parse("2021-10-11T00:00Z"),
                OffsetDateTime.parse("2021-10-24T00:00Z"),
                OffsetDateTime.parse("2021-10-11T10:18Z"));

        assertEquals(0, processFile1.compareTo(processFile2));
        assertEquals(0, processFile1.compareTo(processFile3));
    }

    @Test
    void compareToNotEqualFilesTest() {
        ProcessFile processFile1 = new ProcessFile(
                "cgm-name1",
                "input",
                "CGM",
                OffsetDateTime.parse("2021-10-11T00:00Z"),
                OffsetDateTime.parse("2021-10-12T00:00Z"),
                OffsetDateTime.parse("2021-10-11T10:18Z"));
        ProcessFile processFile2 = new ProcessFile(
                "cgm-name2",
                "input",
                "CRAC",
                OffsetDateTime.parse("2021-10-11T00:00Z"),
                OffsetDateTime.parse("2021-10-12T00:00Z"),
                OffsetDateTime.parse("2021-10-11T10:18Z"));
        ProcessFile processFile3 = new ProcessFile(
                "cgm-name3",
                "output",
                "CGM",
                OffsetDateTime.parse("2021-10-11T00:00Z"),
                OffsetDateTime.parse("2021-10-12T00:00Z"),
                OffsetDateTime.parse("2021-10-11T10:18Z"));
        ProcessFile processFile4 = new ProcessFile(
                "cgm-name4",
                "input",
                "CGM",
                OffsetDateTime.parse("2021-10-11T12:00Z"),
                OffsetDateTime.parse("2021-10-12T00:00Z"),
                OffsetDateTime.parse("2021-10-11T10:18Z"));
        ProcessFile processFile5 = new ProcessFile(
                "cgm-name5",
                "input",
                "CGM",
                OffsetDateTime.parse("2021-10-11T00:00Z"),
                OffsetDateTime.parse("2021-10-12T00:00Z"),
                OffsetDateTime.parse("2021-10-11T10:30Z"));

        assertNotEquals(0, processFile1.compareTo(processFile2));
        assertNotEquals(0, processFile1.compareTo(processFile3));
        assertNotEquals(0, processFile1.compareTo(processFile4));
        assertNotEquals(0, processFile1.compareTo(processFile5));
    }

    @Test
    void equalFilesTest() {
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
                OffsetDateTime.parse("2021-10-11T10:18Z"));
        ProcessFile processFile3 = new ProcessFile(
                "cgm-name3",
                "input",
                "CGM",
                OffsetDateTime.parse("2021-10-11T00:00Z"),
                OffsetDateTime.parse("2021-10-24T00:00Z"),
                OffsetDateTime.parse("2021-10-11T10:18Z"));

        assertEquals(processFile1, processFile1);
        assertEquals(processFile1, processFile2);
        assertEquals(processFile1, processFile3);
    }

    @Test
    void notEqualFilesTest() {
        ProcessFile processFile1 = new ProcessFile(
                "cgm-name1",
                "input",
                "CGM",
                OffsetDateTime.parse("2021-10-11T00:00Z"),
                OffsetDateTime.parse("2021-10-12T00:00Z"),
                OffsetDateTime.parse("2021-10-11T10:18Z"));
        ProcessFile processFile2 = new ProcessFile(
                "cgm-name2",
                "input",
                "CRAC",
                OffsetDateTime.parse("2021-10-11T00:00Z"),
                OffsetDateTime.parse("2021-10-12T00:00Z"),
                OffsetDateTime.parse("2021-10-11T10:18Z"));
        ProcessFile processFile3 = new ProcessFile(
                "cgm-name3",
                "output",
                "CGM",
                OffsetDateTime.parse("2021-10-11T00:00Z"),
                OffsetDateTime.parse("2021-10-12T00:00Z"),
                OffsetDateTime.parse("2021-10-11T10:18Z"));
        ProcessFile processFile4 = new ProcessFile(
                "cgm-name4",
                "input",
                "CGM",
                OffsetDateTime.parse("2021-10-11T12:00Z"),
                OffsetDateTime.parse("2021-10-12T00:00Z"),
                OffsetDateTime.parse("2021-10-11T10:18Z"));
        ProcessFile processFile5 = new ProcessFile(
                "cgm-name5",
                "input",
                "CGM",
                OffsetDateTime.parse("2021-10-11T00:00Z"),
                OffsetDateTime.parse("2021-10-12T00:00Z"),
                OffsetDateTime.parse("2021-10-11T10:30Z"));

        assertNotEquals(processFile1, processFile2);
        assertNotEquals(processFile1, processFile3);
        assertNotEquals(processFile1, processFile4);
        assertNotEquals(processFile1, processFile5);
        assertNotEquals(processFile1, new Object());
    }
}
