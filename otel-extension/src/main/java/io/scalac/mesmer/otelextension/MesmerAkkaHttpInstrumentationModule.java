package io.scalac.mesmer.otelextension;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.tooling.muzzle.InstrumentationModuleMuzzle;
import io.opentelemetry.javaagent.tooling.muzzle.VirtualFieldMappingsBuilder;
import io.opentelemetry.javaagent.tooling.muzzle.references.ClassRef;
import io.scalac.mesmer.otelextension.instrumentations.akka.http.AkkaHttpAgent;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@AutoService(InstrumentationModule.class)
public class MesmerAkkaHttpInstrumentationModule extends InstrumentationModule
    implements InstrumentationModuleMuzzle {
  public MesmerAkkaHttpInstrumentationModule() {
    super("mesmer-akka-http");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return AkkaHttpAgent.agent().asOtelTypeInstrumentations();
  }

  @Override
  public List<String> getAdditionalHelperClassNames() {
    return Arrays.asList(
        "io.scalac.mesmer.otelextension.instrumentations.akka.http.AkkaHttpConnectionsInstrumentation$",
        "io.scalac.mesmer.otelextension.instrumentations.akka.http.AkkaHttpConnectionsInstrumentation$HttpConnectionInstruments$",
        "io.scalac.mesmer.otelextension.instrumentations.akka.http.AkkaHttpConnectionsInstrumentation",
        "io.scalac.mesmer.instrumentation.http.HttpExtConnectionAdvice");
  }

  @Override
  public Map<String, ClassRef> getMuzzleReferences() {
    return emptyMap();
  }

  @Override
  public void registerMuzzleVirtualFields(VirtualFieldMappingsBuilder builder) {}

  @Override
  public List<String> getMuzzleHelperClassNames() {
    return emptyList();
  }
}
