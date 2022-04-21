package io.scalac.mesmer.otelextension;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.scalac.mesmer.otelextension.instrumentations.akka.persistence.AkkaPersistenceAgent;

import java.util.Arrays;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class MesmerAkkaPersistenceInstrumentationModule extends InstrumentationModule {
  public MesmerAkkaPersistenceInstrumentationModule() {
    super("mesmer-akka-persistence");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return AkkaPersistenceAgent.agent().asOtelTypeInstrumentations();
  }

  @Override
  public List<String> getAdditionalHelperClassNames() {
    return Arrays.asList(
            "io.scalac.mesmer.otelextension.instrumentations.akka.persistence.impl.JournalInteractionsAdvice$",
            "io.scalac.mesmer.otelextension.instrumentations.akka.persistence.impl.StoringSnapshotAdvice$",
            "io.scalac.mesmer.otelextension.instrumentations.akka.persistence.impl.RecoveryStartedAdvice$",
            "io.scalac.mesmer.otelextension.instrumentations.akka.persistence.impl.PersistingEventSuccessAdvice$",
            "io.scalac.mesmer.otelextension.instrumentations.akka.persistence.impl.RecoveryCompletedAdvice$"
    );
  }
}
