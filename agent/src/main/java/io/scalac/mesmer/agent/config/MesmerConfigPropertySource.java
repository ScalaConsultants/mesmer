package io.scalac.mesmer.agent.config;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.config.ConfigPropertySource;

import java.util.Map;

@AutoService(ConfigPropertySource.class)
public class MesmerConfigPropertySource implements ConfigPropertySource {

    @Override
    public Map<String, String> getProperties() {
        return MesmerConfigPropertySourceProvider.getProperties();
    }
}
