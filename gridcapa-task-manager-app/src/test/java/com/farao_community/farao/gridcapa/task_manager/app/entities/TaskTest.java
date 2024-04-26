/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app.entities;

import com.farao_community.farao.gridcapa.task_manager.api.TaskStatus;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
class TaskTest {

    private Task task;

    @BeforeEach
    public void setUp() {
        task = new Task(OffsetDateTime.parse("2021-01-01T00:00Z"));
    }

    @Test
    void setId() {
        UUID newId = UUID.randomUUID();
        task.setId(newId);
        assertEquals(newId, task.getId());
    }

    @Test
    void setTimestamp() {
        OffsetDateTime newDateTime = OffsetDateTime.parse("2021-01-01T00:00Z");
        assertEquals(newDateTime, task.getTimestamp());
    }

    @Test
    void setStatus() {
        assert task.getStatus().equals(TaskStatus.CREATED);
        task.setStatus(TaskStatus.READY);
        assertEquals(TaskStatus.READY, task.getStatus());
    }

    @Test
    void addProcessFileOutput() {
        ProcessFile processFileOutput = new ProcessFile(
                "cne-file",
                "output",
                "CNE",
                OffsetDateTime.parse("2021-10-11T00:00Z"),
                OffsetDateTime.parse("2021-10-12T00:00Z"),
                OffsetDateTime.parse("2021-10-11T10:18Z"));

        task.addProcessFile(processFileOutput);

        Assertions.assertThat(task.getProcessFiles())
                .hasSize(1)
                .containsExactly(processFileOutput);

        // Add the same file again, it should still appear only once in the processFiles collection
        task.addProcessFile(processFileOutput);

        Assertions.assertThat(task.getProcessFiles())
                .hasSize(1)
                .containsExactly(processFileOutput);

        // Add another output file
        ProcessFile processFileOutput2 = new ProcessFile(
                "ttc-file",
                "output",
                "TTC",
                OffsetDateTime.parse("2021-10-11T00:00Z"),
                OffsetDateTime.parse("2021-10-12T00:00Z"),
                OffsetDateTime.parse("2021-10-11T10:20Z"));

        task.addProcessFile(processFileOutput2);

        Assertions.assertThat(task.getProcessFiles())
                .hasSize(2)
                .containsExactly(processFileOutput, processFileOutput2);
    }

    @Test
    void addProcessFileInput() {
        ProcessFile processFileInput = new ProcessFile(
                "cgm-file",
                "input",
                "CGM",
                OffsetDateTime.parse("2021-10-11T00:00Z"),
                OffsetDateTime.parse("2021-10-12T00:00Z"),
                OffsetDateTime.parse("2021-10-11T10:18Z"));

        task.addProcessFile(processFileInput);

        Assertions.assertThat(task.getProcessFiles())
                .hasSize(1)
                .containsExactly(processFileInput);
        Assertions.assertThat(task.getAvailableInputs("CGM"))
                .hasSize(1)
                .containsExactly(processFileInput);

        // Add the same file again, it should still appear only once in the processFiles collection
        task.addProcessFile(processFileInput);

        Assertions.assertThat(task.getProcessFiles())
                .hasSize(1)
                .containsExactly(processFileInput);
        Assertions.assertThat(task.getAvailableInputs("CGM"))
                .hasSize(1)
                .containsExactly(processFileInput);

        // Add another output file
        ProcessFile processFileInput2 = new ProcessFile(
                "CRAC-file",
                "input",
                "CRAC",
                OffsetDateTime.parse("2021-10-11T00:00Z"),
                OffsetDateTime.parse("2021-10-12T00:00Z"),
                OffsetDateTime.parse("2021-10-11T10:20Z"));

        task.addProcessFile(processFileInput2);

        Assertions.assertThat(task.getProcessFiles())
                .hasSize(2)
                .containsExactly(processFileInput, processFileInput2);
        Assertions.assertThat(task.getAvailableInputs("CGM"))
                .hasSize(1)
                .containsExactly(processFileInput);
        Assertions.assertThat(task.getAvailableInputs("CRAC"))
                .hasSize(1)
                .containsExactly(processFileInput2);
    }

    @Test
    void addProcessFileInputWithSameFilename() {
        ProcessFile processFileInput = new ProcessFile(
                "cgm-file",
                "input",
                "CGM",
                OffsetDateTime.parse("2021-10-11T00:00Z"),
                OffsetDateTime.parse("2021-10-12T00:00Z"),
                OffsetDateTime.parse("2021-10-11T10:18Z"));

        task.addProcessFile(processFileInput);

        Assertions.assertThat(task.getProcessFiles())
                .hasSize(1)
                .containsExactly(processFileInput);
        Assertions.assertThat(task.getAvailableInputs("CGM"))
                .hasSize(1)
                .containsExactly(processFileInput);

        // Add another output file with same filename
        ProcessFile processFileInput2 = new ProcessFile(
                "cgm-file",
                "input",
                "CGM",
                OffsetDateTime.parse("2021-10-11T00:00Z"),
                OffsetDateTime.parse("2021-10-12T00:00Z"),
                OffsetDateTime.parse("2021-10-11T22:20Z"));

        task.addProcessFile(processFileInput2);

        Assertions.assertThat(task.getProcessFiles())
                .hasSize(1)
                .containsExactly(processFileInput2);
        Assertions.assertThat(task.getAvailableInputs("CGM"))
                .hasSize(1)
                .containsExactly(processFileInput2);
    }

    @Test
    void addProcessFileInputWithSameFileType() {
        ProcessFile processFileInput = new ProcessFile(
                "cgm-file",
                "input",
                "CGM",
                OffsetDateTime.parse("2021-10-11T00:00Z"),
                OffsetDateTime.parse("2021-10-12T00:00Z"),
                OffsetDateTime.parse("2021-10-11T10:18Z"));

        task.addProcessFile(processFileInput);

        Assertions.assertThat(task.getProcessFiles())
                .hasSize(1)
                .containsExactly(processFileInput);
        Assertions.assertThat(task.getAvailableInputs("CGM"))
                .hasSize(1)
                .containsExactly(processFileInput);

        // Add another output file with same file type but different filename, the new file should be selected
        ProcessFile processFileInput2 = new ProcessFile(
                "other-cgm-file",
                "input",
                "CGM",
                OffsetDateTime.parse("2021-10-11T00:00Z"),
                OffsetDateTime.parse("2021-10-12T00:00Z"),
                OffsetDateTime.parse("2021-10-11T12:20Z"));

        task.addProcessFile(processFileInput2);

        Assertions.assertThat(task.getProcessFiles())
                .hasSize(1)
                .containsExactly(processFileInput2);
        Assertions.assertThat(task.getAvailableInputs("CGM"))
                .hasSize(2)
                .containsExactly(processFileInput, processFileInput2);

        // Add first file again, it should be selected
        task.addProcessFile(processFileInput);

        Assertions.assertThat(task.getProcessFiles())
                .hasSize(1)
                .containsExactly(processFileInput);
        Assertions.assertThat(task.getAvailableInputs("CGM"))
                .hasSize(2)
                .containsExactly(processFileInput, processFileInput2);
    }

    @Test
    void removeOutputProcessFile() {
        // Given
        ProcessFile processFileOutput = new ProcessFile(
                "cne-file",
                "output",
                "CNE",
                OffsetDateTime.parse("2021-10-11T00:00Z"),
                OffsetDateTime.parse("2021-10-12T00:00Z"),
                OffsetDateTime.parse("2021-10-11T10:18Z"));
        ProcessFile processFileOutput2 = new ProcessFile(
                "ttc-file",
                "output",
                "TTC",
                OffsetDateTime.parse("2021-10-11T00:00Z"),
                OffsetDateTime.parse("2021-10-12T00:00Z"),
                OffsetDateTime.parse("2021-10-11T10:20Z"));

        task.addProcessFile(processFileOutput);
        task.addProcessFile(processFileOutput2);

        // When
        FileRemovalStatus fileRemovalStatus = task.removeProcessFile(processFileOutput2);

        // Then
        Assertions.assertThat(task.getProcessFiles())
                .hasSize(1)
                .containsExactly(processFileOutput);
        Assertions.assertThat(fileRemovalStatus.fileRemoved()).isTrue();
        Assertions.assertThat(fileRemovalStatus.fileSelectionUpdated()).isTrue();
    }

    @Test
    void removeNotSelectedInputProcessFile() {
        // Given
        ProcessFile processFileInput = new ProcessFile(
                "cgm-file",
                "input",
                "CGM",
                OffsetDateTime.parse("2021-10-11T00:00Z"),
                OffsetDateTime.parse("2021-10-12T00:00Z"),
                OffsetDateTime.parse("2021-10-11T10:18Z"));
        ProcessFile processFileInput2 = new ProcessFile(
                "other-cgm-file",
                "input",
                "CGM",
                OffsetDateTime.parse("2021-10-11T00:00Z"),
                OffsetDateTime.parse("2021-10-12T00:00Z"),
                OffsetDateTime.parse("2021-10-11T12:20Z"));

        task.addProcessFile(processFileInput);
        task.addProcessFile(processFileInput2);

        // When
        FileRemovalStatus fileRemovalStatus = task.removeProcessFile(processFileInput);

        // Then
        Assertions.assertThat(task.getProcessFiles())
                .hasSize(1)
                .containsExactly(processFileInput2);
        Assertions.assertThat(task.getAvailableInputs("CGM"))
                .hasSize(1)
                .containsExactly(processFileInput2);
        Assertions.assertThat(fileRemovalStatus.fileRemoved()).isTrue();
        Assertions.assertThat(fileRemovalStatus.fileSelectionUpdated()).isFalse();
    }

    @Test
    void removeSelectedInputProcessFileWithOtherVersionAvailable() {
        // Given
        ProcessFile processFileInput = new ProcessFile(
                "cgm-file",
                "input",
                "CGM",
                OffsetDateTime.parse("2021-10-11T00:00Z"),
                OffsetDateTime.parse("2021-10-12T00:00Z"),
                OffsetDateTime.parse("2021-10-11T10:18Z"));
        ProcessFile processFileInput2 = new ProcessFile(
                "other-cgm-file",
                "input",
                "CGM",
                OffsetDateTime.parse("2021-10-11T00:00Z"),
                OffsetDateTime.parse("2021-10-12T00:00Z"),
                OffsetDateTime.parse("2021-10-11T12:20Z"));

        task.addProcessFile(processFileInput);
        task.addProcessFile(processFileInput2);

        // When
        FileRemovalStatus fileRemovalStatus = task.removeProcessFile(processFileInput2);

        // Then
        Assertions.assertThat(task.getProcessFiles())
                .hasSize(1)
                .containsExactly(processFileInput);
        Assertions.assertThat(task.getAvailableInputs("CGM"))
                .hasSize(1)
                .containsExactly(processFileInput);
        Assertions.assertThat(fileRemovalStatus.fileRemoved()).isTrue();
        Assertions.assertThat(fileRemovalStatus.fileSelectionUpdated()).isTrue();
    }

    @Test
    void removeSelectedInputProcessFileWithoutOtherVersionAvailable() {
        // Given
        ProcessFile processFileInput = new ProcessFile(
                "cgm-file",
                "input",
                "CGM",
                OffsetDateTime.parse("2021-10-11T00:00Z"),
                OffsetDateTime.parse("2021-10-12T00:00Z"),
                OffsetDateTime.parse("2021-10-11T10:18Z"));

        task.addProcessFile(processFileInput);

        // When
        FileRemovalStatus fileRemovalStatus = task.removeProcessFile(processFileInput);

        // Then
        Assertions.assertThat(task.getProcessFiles()).isEmpty();
        Assertions.assertThat(task.getAvailableInputs("CGM")).isEmpty();
        Assertions.assertThat(fileRemovalStatus.fileRemoved()).isTrue();
        Assertions.assertThat(fileRemovalStatus.fileSelectionUpdated()).isTrue();
    }

    @Test
    void removeInexistantFile() {
        // Given
        ProcessFile processFileInput = new ProcessFile(
                "cgm-file",
                "input",
                "CGM",
                OffsetDateTime.parse("2021-10-11T00:00Z"),
                OffsetDateTime.parse("2021-10-12T00:00Z"),
                OffsetDateTime.parse("2021-10-11T10:18Z"));

        // When
        FileRemovalStatus fileRemovalStatus = task.removeProcessFile(processFileInput);

        // Then
        Assertions.assertThat(task.getProcessFiles()).isEmpty();
        Assertions.assertThat(task.getAvailableInputs("CGM")).isEmpty();
        Assertions.assertThat(fileRemovalStatus.fileRemoved()).isFalse();
        Assertions.assertThat(fileRemovalStatus.fileSelectionUpdated()).isFalse();
    }

    @Test
    void selectProcessFile() {
        // Given
        ProcessFile processFileInput = new ProcessFile(
                "cgm-file",
                "input",
                "CGM",
                OffsetDateTime.parse("2021-10-11T00:00Z"),
                OffsetDateTime.parse("2021-10-12T00:00Z"),
                OffsetDateTime.parse("2021-10-11T10:18Z"));
        ProcessFile processFileInput2 = new ProcessFile(
                "other-cgm-file",
                "input",
                "CGM",
                OffsetDateTime.parse("2021-10-11T00:00Z"),
                OffsetDateTime.parse("2021-10-12T00:00Z"),
                OffsetDateTime.parse("2021-10-11T12:20Z"));

        task.addProcessFile(processFileInput);
        task.addProcessFile(processFileInput2);

        // Whe
        task.selectProcessFile(processFileInput);

        // Then
        Assertions.assertThat(task.getProcessFiles())
                .hasSize(1)
                .containsExactly(processFileInput);
        Assertions.assertThat(task.getAvailableInputs("CGM"))
                .hasSize(2)
                .containsExactly(processFileInput, processFileInput2);
    }

    @Test
    void getInput() {
        Assertions.assertThat(task.getProcessFiles()).isEmpty();
        ProcessFile processFileInput = new ProcessFile(
                "cgm-file",
                "input",
                "CGM",
                OffsetDateTime.parse("2021-10-11T00:00Z"),
                OffsetDateTime.parse("2021-10-12T00:00Z"),
                OffsetDateTime.parse("2021-10-11T10:18Z"));
        ProcessFile processFileOutput = new ProcessFile(
                "cne-file",
                "output",
                "CNE",
                OffsetDateTime.parse("2021-10-11T00:00Z"),
                OffsetDateTime.parse("2021-10-12T00:00Z"),
                OffsetDateTime.parse("2021-10-11T10:18Z"));

        task.addProcessFile(processFileInput);
        task.addProcessFile(processFileOutput);

        Assertions.assertThat(task.getInput("CGM")).contains(processFileInput);
    }

    @Test
    void getAvailableInputs() {
        ProcessFile processFileInput = new ProcessFile(
                "cgm-file",
                "input",
                "CGM",
                OffsetDateTime.parse("2021-10-11T00:00Z"),
                OffsetDateTime.parse("2021-10-12T00:00Z"),
                OffsetDateTime.parse("2021-10-11T10:18Z"));

        task.addProcessFile(processFileInput);

        ProcessFile processFileInput2 = new ProcessFile(
                "glsk-file",
                "input",
                "GLSK",
                OffsetDateTime.parse("2021-10-11T00:00Z"),
                OffsetDateTime.parse("2021-10-12T00:00Z"),
                OffsetDateTime.parse("2021-10-11T10:18Z"));

        task.addProcessFile(processFileInput2);

        Assertions.assertThat(task.getAvailableInputs("CGM"))
                .hasSize(1)
                .containsExactly(processFileInput);

        ProcessFile processFileInput3 = new ProcessFile(
                "other-cgm-file",
                "input",
                "CGM",
                OffsetDateTime.parse("2021-10-11T00:00Z"),
                OffsetDateTime.parse("2021-10-12T00:00Z"),
                OffsetDateTime.parse("2021-10-11T22:20Z"));

        task.addProcessFile(processFileInput3);

        Assertions.assertThat(task.getAvailableInputs("CGM"))
                .hasSize(2)
                .containsExactly(processFileInput, processFileInput3);
    }

    @Test
    void getOutput() {
        Assertions.assertThat(task.getProcessFiles()).isEmpty();
        ProcessFile processFileInput = new ProcessFile(
                "cgm-file",
                "input",
                "CGM",
                OffsetDateTime.parse("2021-10-11T00:00Z"),
                OffsetDateTime.parse("2021-10-12T00:00Z"),
                OffsetDateTime.parse("2021-10-11T10:18Z"));
        ProcessFile processFileOutput = new ProcessFile(
                "cne-file",
                "output",
                "CNE",
                OffsetDateTime.parse("2021-10-11T00:00Z"),
                OffsetDateTime.parse("2021-10-12T00:00Z"),
                OffsetDateTime.parse("2021-10-11T10:18Z"));

        task.addProcessFile(processFileInput);
        task.addProcessFile(processFileOutput);

        Assertions.assertThat(task.getOutput("CNE")).contains(processFileOutput);
    }

    @Test
    void getProcessEventsTest() {
        assertTrue(task.getProcessEvents().isEmpty());
        task.addProcessEvent(OffsetDateTime.now(), "WARN", "test", "serviceName");
        assertEquals("WARN", task.getProcessEvents().iterator().next().getLevel());
    }
}
