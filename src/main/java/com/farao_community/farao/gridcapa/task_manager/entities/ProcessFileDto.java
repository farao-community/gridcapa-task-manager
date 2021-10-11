/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.entities;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
public class ProcessFileDto {
    private final String fileType;
    private final ProcessFileStatus processFileStatus;
    private final String filename;
    private final LocalDateTime lastModificationDate;
    private final String fileUrl;

    @JsonCreator
    public ProcessFileDto(@JsonProperty String fileType,
                          @JsonProperty ProcessFileStatus processFileStatus,
                          @JsonProperty String filename,
                          @JsonProperty LocalDateTime lastModificationDate,
                          @JsonProperty String fileUrl) {
        this.fileType = fileType;
        this.processFileStatus = processFileStatus;
        this.filename = filename;
        this.lastModificationDate = lastModificationDate;
        this.fileUrl = fileUrl;
    }

    public static ProcessFileDto fromEntity(ProcessFile processFile) {
        return new ProcessFileDto(
                processFile.getFileType(),
                processFile.getProcessFileStatus(),
                processFile.getFilename(),
                processFile.getLastModificationDate(),
                processFile.getFileUrl());
    }

    public static ProcessFileDto emptyProcessFile(String fileType) {
        return new ProcessFileDto(
                fileType,
                ProcessFileStatus.NOT_PRESENT,
                null,
                null,
                null);
    }

    public String getFileType() {
        return fileType;
    }

    public ProcessFileStatus getProcessFileStatus() {
        return processFileStatus;
    }

    public String getFilename() {
        return filename;
    }

    public LocalDateTime getLastModificationDate() {
        return lastModificationDate;
    }

    public String getFileUrl() {
        return fileUrl;
    }
}
