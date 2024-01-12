package com.farao_community.farao.gridcapa.task_manager.app.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@ConfigurationProperties("runner")
public class RunnerParameters {

    private final Map<String, String> parameters = new HashMap<>();

    public Map<String, String> getParameters() {
        return parameters;
    }

    public Optional<String> getRunnerParamater(String id) {
        return Optional.ofNullable(parameters.get(id));
    }
}
