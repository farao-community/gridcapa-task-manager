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
