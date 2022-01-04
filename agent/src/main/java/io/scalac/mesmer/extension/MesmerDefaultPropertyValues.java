package io.scalac.mesmer.extension;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.config.ConfigPropertySource;

import java.util.HashMap;
import java.util.Map;

@AutoService(ConfigPropertySource.class)
public class MesmerDefaultPropertyValues implements ConfigPropertySource {

    @Override
    public Map<String, String> getProperties() {
        System.out.println("DemoPropertySource");
        Map<String, String> properties = new HashMap<>();
        properties.put("otel.mesmer.someprop", "someprop, a default value");
        return properties;
    }
}