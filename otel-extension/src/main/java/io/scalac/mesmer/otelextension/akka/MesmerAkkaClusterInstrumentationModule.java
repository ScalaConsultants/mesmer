package io.scalac.mesmer.otelextension.akka;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.scalac.mesmer.otelextension.instrumentations.akka.cluster.AkkaClusterAgent;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class MesmerAkkaClusterInstrumentationModule extends InstrumentationModule {
  public MesmerAkkaClusterInstrumentationModule() {
    super("mesmer-akka-cluster");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return List.of(AkkaClusterAgent.clusterMetricsExtension());
  }

  @Override
  public boolean isHelperClass(String className) {
    if (className.matches("io.scalac.mesmer.otelextension.instrumentations.akka.cluster.*")
        || className.matches("io.scalac.mesmer.otelextension.instrumentations.akka.common.*")
        || className.matches("io.scalac.mesmer.core.*")) {
      return true;
    } else {
      return super.isHelperClass(className);
    }
  }
}
