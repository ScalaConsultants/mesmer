package io.scalac.mesmer.otelextension.zio;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.tooling.muzzle.InstrumentationModuleMuzzle;
import io.opentelemetry.javaagent.tooling.muzzle.VirtualFieldMappingsBuilder;
import io.opentelemetry.javaagent.tooling.muzzle.references.ClassRef;
import io.scalac.mesmer.otelextension.instrumentations.zio.ZIOInstrumentations;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@AutoService(InstrumentationModule.class)
public class MesmerZIOMetricAPIInstrumentationModule extends InstrumentationModule
    implements InstrumentationModuleMuzzle {
  public MesmerZIOMetricAPIInstrumentationModule() {
    super("mesmer-zio-metrics-api");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return Collections.singletonList(ZIOInstrumentations.concurrentMetricRegistryAdvice());
  }

  @Override
  public List<String> getAdditionalHelperClassNames() {
    return List.of(
        "io.scalac.mesmer.otelextension.instrumentations.zio.advice.ConcurrentMetricRegistryAdvice$",
        "io.scalac.mesmer.otelextension.instrumentations.zio.ZIOInstrumentations$",
        "io.scalac.mesmer.otelextension.instrumentations.zio.ZIOMetrics$",
        "io.scalac.mesmer.otelextension.instrumentations.zio.ConcurrentMetricRegistryPoller",
        "io.scalac.mesmer.otelextension.instrumentations.zio.ConcurrentMetricRegistryClient$");
  }

  @Override
  public Map<String, ClassRef> getMuzzleReferences() {
    return Collections.emptyMap();
  }

  @Override
  public void registerMuzzleVirtualFields(VirtualFieldMappingsBuilder builder) { }

  @Override
  public List<String> getMuzzleHelperClassNames() {
    return Collections.emptyList();
  }
}
