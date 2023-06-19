/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app.entities;

public class ProcessFileMinio {

    private final ProcessFile processFile;
    private final FileEventType fileEventType;

    public ProcessFileMinio(ProcessFile processFile, FileEventType fileEventType) {
        this.processFile = processFile;
        this.fileEventType = fileEventType;
    }

    public ProcessFile getProcessFile() {
        return processFile;
    }

    public FileEventType getFileEventType() {
        return fileEventType;
    }

    public boolean hasSameTypeAndValidity(ProcessFileMinio newProcessFileMinio) { //todo use process file equals
        return this.getProcessFile().getFileType().equals(newProcessFileMinio.getProcessFile().getFileType())
                && this.getProcessFile().getStartingAvailabilityDate().equals(newProcessFileMinio.getProcessFile().getStartingAvailabilityDate())
                && this.getProcessFile().getEndingAvailabilityDate().equals(newProcessFileMinio.getProcessFile().getEndingAvailabilityDate());
    }
}
