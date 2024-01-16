package com.farao_community.farao.gridcapa.task_manager.app.configuration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;
import java.util.Optional;

@SpringBootTest
class RunnerParametersTest {

    @Autowired
    RunnerParameters runnerParameters;

    @Test
    void getParametersTest() {
        Map<String, String>  parameters = runnerParameters.getParameters();
        String test1Value = parameters.get("test1");
        Assertions.assertNotNull(test1Value);
        Assertions.assertEquals("true", test1Value);
        String test2Value = parameters.get("test2");
        Assertions.assertNotNull(test2Value);
        Assertions.assertEquals("42", test2Value);
        Assertions.assertNull(parameters.get("inexistant"));
    }

    @Test
    void getRunnerParameterTest() {
        Optional<String> test1Value = runnerParameters.getRunnerParameter("test1");
        Assertions.assertTrue(test1Value.isPresent());
        Assertions.assertEquals("true", test1Value.get());
        Assertions.assertTrue(runnerParameters.getRunnerParameter("toto").isEmpty());

    }
}
