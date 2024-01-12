/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app.service;

import com.farao_community.farao.gridcapa.task_manager.api.ParameterDto;
import com.farao_community.farao.gridcapa.task_manager.api.TaskManagerException;
import com.farao_community.farao.gridcapa.task_manager.app.ParameterRepository;
import com.farao_community.farao.gridcapa.task_manager.app.configuration.RunnerParameters;
import com.farao_community.farao.gridcapa.task_manager.app.entities.Parameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * @author Vincent Bochet {@literal <vincent.bochet at rte-france.com>}
 * @author Marc Schwitzguebel {@literal <marc.schwitzguebel at rte-france.com>}
 */

@Service
public class ParameterService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ParameterService.class);

    private final ParameterRepository parameterRepository;

    private final RunnerParameters runnerParameters;

    public ParameterService(ParameterRepository parameterRepository, RunnerParameters runnerParameters) {
        this.parameterRepository = parameterRepository;
        this.runnerParameters = runnerParameters;
    }

    public List<ParameterDto> getParameters() {
        List<Parameter> parameters = parameterRepository.findAll();
        List<ParameterDto> dtos = new ArrayList<>();
        for (Parameter param : parameters) {
            String defaultValue = runnerParameters.getRunnerParamater(param.getId()).orElseThrow(() -> new TaskManagerException("No default value for given parameter"));
            dtos.add(new ParameterDto(param.getId(), param.getName(), param.getDisplayOrder(), param.getParameterType().name(), param.getSectionTitle(), param.getValue(), defaultValue));
        }

        return dtos;
    }

    public ParameterDto setParameterValue(String id, String value) {
        LOGGER.info("Setting parameter {} to value {}", id, value);
        Optional<Parameter> parameterOpt = parameterRepository.findById(id);
        if (parameterOpt.isEmpty()) {
            LOGGER.info("Parameter {} not found", id);
            return null;
        } else {
            Parameter parameter = parameterOpt.get();
            parameter.setValue(value);
            Parameter savedParameter = parameterRepository.save(parameter);
            String defaultValue = runnerParameters.getRunnerParamater(parameter.getId()).orElse(null);
            return new ParameterDto(savedParameter.getId(), savedParameter.getName(), savedParameter.getDisplayOrder(), savedParameter.getParameterType().name(), savedParameter.getSectionTitle(), savedParameter.getValue(), defaultValue);
        }
    }
}
