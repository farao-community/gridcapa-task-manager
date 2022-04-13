/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app;

import com.farao_community.farao.gridcapa.task_manager.api.FileGroup;
import com.farao_community.farao.gridcapa.task_manager.api.TaskNotFoundException;
import com.farao_community.farao.gridcapa.task_manager.app.entities.ProcessFile;
import com.farao_community.farao.gridcapa.task_manager.app.entities.Task;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
@Service
public class FileManager {

    private final TaskRepository taskRepository;
    private final UrlValidationService urlValidationService;

    public FileManager(TaskRepository taskRepository, UrlValidationService urlValidationService) {
        this.taskRepository = taskRepository;
        this.urlValidationService = urlValidationService;
    }

    public String getZippedOutputsName() {
        return "just-a-try.zip";
    }

    public ByteArrayOutputStream getZippedGroup(OffsetDateTime timestamp, FileGroup fileGroup) throws IOException {
        Optional<Task> optTask = taskRepository.findByTimestamp(timestamp);
        if (optTask.isPresent()) {
            Task task = optTask.get();
            return getZippedFileGroup(task, fileGroup);
        } else {
            throw new TaskNotFoundException();
        }
    }

    private ByteArrayOutputStream getZippedFileGroup(Task task, FileGroup fileGroup) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            Set<ProcessFile> outputProcessFiles = getProcessFiles(task, fileGroup);
            for (ProcessFile processFile : outputProcessFiles) {
                writeZipEntry(zos, processFile);
            }
            return baos;
        }
    }

    private Set<ProcessFile> getProcessFiles(Task task, FileGroup fileGroup) {
        return task.getProcessFiles().stream().filter(processFile -> processFile.getFileGroup() == fileGroup).collect(Collectors.toSet());
    }

    private void writeZipEntry(ZipOutputStream zos, ProcessFile processFile) throws IOException {
        try (InputStream is = urlValidationService.openUrlStream(processFile.getFileUrl())) {
            zos.putNextEntry(new ZipEntry(processFile.getFilename()));
            byte[] buffer = new byte[1024];
            int len;
            while ((len = is.read(buffer)) > 0) {
                zos.write(buffer, 0, len);
            }
        }
    }
}
