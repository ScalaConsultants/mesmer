package io.scalac.mesmer.otelextension.akka;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.scalac.mesmer.otelextension.instrumentations.akka.persistence.AkkaPersistenceAgent;
import java.util.Arrays;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class MesmerAkkaPersistenceInstrumentationModule extends InstrumentationModule {
  //    implements InstrumentationModuleMuzzle {
  public MesmerAkkaPersistenceInstrumentationModule() {
    super("mesmer-akka-persistence");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return Arrays.asList(
        AkkaPersistenceAgent.actorSystemPersistenceProvider(),
        AkkaPersistenceAgent.replayingEventsOnRecoveryComplete(),
        AkkaPersistenceAgent.replayingSnapshotOnRecoveryStart(),
        AkkaPersistenceAgent.runningOnWriteInitiatedInstrumentation(),
        AkkaPersistenceAgent.runningOnWriteSuccessInstrumentation(),
        AkkaPersistenceAgent.abstractBehaviorSubstituteTest(),
        AkkaPersistenceAgent.storingSnapshotOnWriteInitiated());
  }

  @Override
  public boolean isHelperClass(String className) {
    if (className.matches("io.scalac.mesmer.otelextension.instrumentations.akka.persistence.*")
        || className.matches("io.scalac.mesmer.otelextension.instrumentations.akka.common.*")
        || className.matches("io.scalac.mesmer.configuration.*")
        || className.matches("io.scalac.mesmer.core.*")) {
      return true;
    } else {
      return super.isHelperClass(className);
    }
  }
}
