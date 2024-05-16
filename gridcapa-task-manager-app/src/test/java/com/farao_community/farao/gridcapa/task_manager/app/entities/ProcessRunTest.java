/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app.entities;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * @author Vincent Bochet {@literal <vincent.bochet at rte-france.com>}
 */
class ProcessRunTest {
    @Test
    void valuedConstructorTest() {
        ProcessFile file1 = new ProcessFile();
        ProcessFile file2 = new ProcessFile();
        ProcessRun processRun = new ProcessRun(List.of(file1, file2));

        Assertions.assertThat(processRun).isNotNull();
        Assertions.assertThat(processRun.getId()).isNotNull();
        Assertions.assertThat(processRun.getExecutionDate()).isNotNull().isBetween(OffsetDateTime.now().minusMinutes(1), OffsetDateTime.now());
        Assertions.assertThat(processRun.getInputFiles()).isNotNull().containsExactly(file1, file2);
    }

    @Test
    void removeInputFileByFilenameTest() {
        OffsetDateTime now = OffsetDateTime.now();
        ProcessFile file1 = new ProcessFile("first-file", "input", "CGM", now, now, now);
        ProcessFile file2 = new ProcessFile("second-file", "input", "CGM", now, now, now);
        ProcessRun processRun = new ProcessRun(List.of(file1, file2));

        Assertions.assertThat(processRun.getInputFiles()).isNotNull().containsExactly(file1, file2);

        processRun.removeInputFileByFilename("first-file");

        Assertions.assertThat(processRun.getInputFiles()).isNotNull().containsExactly(file2);
    }
}

