/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager;

import com.farao_community.farao.gridcapa.task_manager.entities.Task;
import com.farao_community.farao.gridcapa.task_manager.entities.TaskDto;
import org.springframework.cloud.stream.config.BindingServiceProperties;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Service;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
@Service
public class TaskNotifier {
    private static final String TASK_UPDATED_BINDING = "task-updated";

    private final StreamBridge streamBridge;

    private final BindingServiceProperties bindingServiceProperties;

    public TaskNotifier(StreamBridge streamBridge, BindingServiceProperties bindingServiceProperties) {
        this.streamBridge = streamBridge;
        this.bindingServiceProperties = bindingServiceProperties;
    }

    public void notifyUpdate(Task task) {
        String bindingName = bindingServiceProperties.getBindings().keySet().stream().findFirst().orElse(TASK_UPDATED_BINDING);
        streamBridge.send(bindingName, TaskDto.fromEntity(task));
    }
}
