package io.scalac.mesmer.otelextension;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.scalac.mesmer.agent.akka.http.AkkaHttpAgent;

import java.util.List;

@AutoService(InstrumentationModule.class)
public class MesmerAkkaHttpInstrumentationModule extends InstrumentationModule {
  public MesmerAkkaHttpInstrumentationModule() {
    super("mesmer-akka-http");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return AkkaHttpAgent.agent().asOtelTypeInstrumentations();
  }
}
