/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.gridcapa.task_manager.app.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Marc Schwitzguebel {@literal <marc.schwitzguebel at rte-france.com>}
 */
@SpringBootTest
class RunnerParametersTest {

    @Autowired
    RunnerParameters runnerParameters;

    @Test
    void getParametersTest() {
        Map<String, String>  parameters = runnerParameters.getParameters();
        String test1Value = parameters.get("test1");
        assertNotNull(test1Value);
        assertEquals("true", test1Value);
        String test2Value = parameters.get("test2");
        assertNotNull(test2Value);
        assertEquals("42", test2Value);
        assertNull(parameters.get("inexistant"));
    }

    @Test
    void getRunnerParameterTest() {
        Optional<String> test1Value = runnerParameters.getRunnerParameter("test1");
        assertTrue(test1Value.isPresent());
        assertEquals("true", test1Value.get());
        assertTrue(runnerParameters.getRunnerParameter("toto").isEmpty());
    }
}
