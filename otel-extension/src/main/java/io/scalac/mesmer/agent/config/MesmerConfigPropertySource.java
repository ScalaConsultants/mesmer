package io.scalac.mesmer.agent.config;

import com.google.auto.service.AutoService;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import java.util.Map;

@AutoService(AutoConfigurationCustomizerProvider.class)
public class MesmerConfigPropertySource implements AutoConfigurationCustomizerProvider {

  public Map<String, String> getProperties() {
    return MesmerConfigPropertySourceProvider.getProperties();
  }

  @Override
  public void customize(AutoConfigurationCustomizer autoConfiguration) {
    autoConfiguration.addPropertiesSupplier(this::getProperties);
  }
}
