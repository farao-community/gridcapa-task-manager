/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app.service;

import com.farao_community.farao.gridcapa.task_manager.api.ParameterDto;
import com.farao_community.farao.gridcapa.task_manager.app.ParameterRepository;
import com.farao_community.farao.gridcapa.task_manager.app.entities.Parameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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

    public ParameterService(ParameterRepository parameterRepository) {
        this.parameterRepository = parameterRepository;
    }

    public List<ParameterDto> getParameters() {
        List<Parameter> parameters = parameterRepository.findAll();
        return parameters.stream()
            .map(p -> new ParameterDto(p.getId(), p.getName(), p.getDisplayOrder(), p.getParameterType().name(), p.getSectionTitle(), p.getValue()))
            .toList();
    }

    public ParameterDto setParameterValue(Long id, String value) {
        LOGGER.info("Setting parameter {} to value {}", id, value);
        Optional<Parameter> parameterOpt = parameterRepository.findById(id);
        if (parameterOpt.isEmpty()) {
            LOGGER.info("Parameter {} not found", id);
            return null;
        } else {
            Parameter parameter = parameterOpt.get();
            parameter.setValue(value);
            Parameter savedParameter = parameterRepository.save(parameter);
            return new ParameterDto(savedParameter.getId(), savedParameter.getName(), savedParameter.getDisplayOrder(), savedParameter.getParameterType().name(), savedParameter.getSectionTitle(), savedParameter.getValue());
        }
    }
}
