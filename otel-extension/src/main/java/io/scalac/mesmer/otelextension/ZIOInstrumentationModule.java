package io.scalac.mesmer.otelextension;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.tooling.muzzle.InstrumentationModuleMuzzle;
import io.opentelemetry.javaagent.tooling.muzzle.VirtualFieldMappingsBuilder;
import io.opentelemetry.javaagent.tooling.muzzle.references.ClassRef;
import io.scalac.mesmer.zio.ZIORuntimeInstrumentations;
import java.util.List;
import java.util.Map;

@AutoService(InstrumentationModule.class)
public class ZIOInstrumentationModule extends InstrumentationModule
    implements InstrumentationModuleMuzzle {

  public ZIOInstrumentationModule() {
    super("zio");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return List.of(ZIORuntimeInstrumentations.runMethodInstrumentation());
  }

  @Override
  public List<String> getAdditionalHelperClassNames() {
    return List.of(
        "io.scalac.mesmer.zio.ZioRuntimeJavaAdvice",
        "io.scalac.mesmer.zio.ZIOMetricsInstrumenter",
        "io.scalac.mesmer.zio.ZIOMetricsInstrumenter$");
  }

  @Override
  public List<String> getMuzzleHelperClassNames() {
    return emptyList();
  }

  @Override
  public Map<String, ClassRef> getMuzzleReferences() {
    return emptyMap();
  }

  @Override
  public void registerMuzzleVirtualFields(VirtualFieldMappingsBuilder builder) {}
}
