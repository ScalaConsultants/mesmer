package io.scalac.mesmer.otelextension.zio;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.scalac.mesmer.otelextension.instrumentations.zio.ZIOInstrumentations;
import java.util.Collections;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class MesmerZIOMetricAPIInstrumentationModule extends InstrumentationModule {
  public MesmerZIOMetricAPIInstrumentationModule() {
    super("mesmer-zio-metrics-api");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return Collections.singletonList(ZIOInstrumentations.concurrentMetricRegistryAdvice());
  }

  @Override
  public boolean isHelperClass(String className) {
    if (className.matches("io.scalac.mesmer.otelextension.instrumentations.zio.*")) {
      return true;
    } else {
      return super.isHelperClass(className);
    }
  }
}
