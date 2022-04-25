/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;

import java.util.List;

/**
 * @author Sebastien Murgey {@literal <sebastien.murgey at rte-france.com>}
 */
@ConstructorBinding
@ConfigurationProperties("task-server")
public class TaskManagerConfigurationProperties {

    private final ProcessProperties process;
    private final List<String> whitelist;

    public TaskManagerConfigurationProperties(ProcessProperties process, List<String> whitelist) {
        this.process = process;
        this.whitelist = whitelist;
    }

    public ProcessProperties getProcess() {
        return process;
    }

    public List<String> getWhitelist() {
        return whitelist;
    }

    public static final class ProcessProperties {
        private final String tag;
        private final String timezone;
        private final List<String> inputs;
        private final List<String> outputs;

        public ProcessProperties(String tag, String timezone, List<String> inputs, List<String> outputs) {
            this.tag = tag;
            this.timezone = timezone;
            this.inputs = inputs;
            this.outputs = outputs;
        }

        public String getTag() {
            return tag;
        }

        public String getTimezone() {
            return timezone;
        }

        public List<String> getInputs() {
            return inputs;
        }

        public List<String> getOutputs() {
            return outputs;
        }
    }
}
