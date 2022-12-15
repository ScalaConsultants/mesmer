package io.scalac.mesmer.otelextension.akka;

import com.google.auto.service.AutoService;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import io.opentelemetry.sdk.metrics.Aggregation;
import io.opentelemetry.sdk.metrics.InstrumentSelector;
import io.opentelemetry.sdk.metrics.View;

@AutoService(AutoConfigurationCustomizerProvider.class)
public class AutoConfigurationCustomizer implements AutoConfigurationCustomizerProvider {

  @Override
  public void customize(
      io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer autoConfiguration) {



    autoConfiguration.addMeterProviderCustomizer(
        (sdkMeterProviderBuilder, configProperties) ->
            sdkMeterProviderBuilder.registerView(
                InstrumentSelector.builder().setName("mesmer_akka_actor_mailbox_size").build(),
                View.builder()
                    .setName("mesmer_akka_actor_mailbox_size_view")
                    .setAttributeFilter(attributeKey -> !attributeKey.equals("bar"))
                    .setAggregation(Aggregation.sum())
                    .build()));
  }
}
