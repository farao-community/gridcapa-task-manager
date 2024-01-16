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
        return parameterRepository.findAll().stream()
            .map(this::convertToDtoAndFillDefaultValue)
            .toList();
    }

    public List<ParameterDto> setParameterValues(List<ParameterDto> parameterDtos) {
        List<Parameter> parametersToSave = new ArrayList<>();

        for (ParameterDto parameterDto : parameterDtos) {
            String id = parameterDto.getId();
            String value = parameterDto.getValue();
            Optional<Parameter> parameterOpt = parameterRepository.findById(id);
            if (parameterOpt.isEmpty()) {
                LOGGER.info("Parameter {} not found", id);
                return null;
            } else {
                LOGGER.info("Setting parameter {} to value {}", id, value);
                Parameter parameter = parameterOpt.get();
                parameter.setValue(value);
                parametersToSave.add(parameter);
            }
        }

        return parameterRepository.saveAll(parametersToSave).stream()
            .map(this::convertToDtoAndFillDefaultValue)
            .toList();
    }

    private ParameterDto convertToDtoAndFillDefaultValue(Parameter param) {
        String defaultValue = runnerParameters.getRunnerParameter(param.getId())
            .orElseThrow(() -> new TaskManagerException("No default value for given parameter"));
        return new ParameterDto(param.getId(), param.getName(), param.getDisplayOrder(), param.getParameterType().name(), param.getSectionTitle(), param.getValue(), defaultValue);
    }
}
