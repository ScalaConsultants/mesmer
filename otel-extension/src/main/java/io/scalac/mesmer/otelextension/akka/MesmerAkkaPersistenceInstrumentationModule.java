package io.scalac.mesmer.otelextension.akka;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.tooling.muzzle.InstrumentationModuleMuzzle;
import io.opentelemetry.javaagent.tooling.muzzle.VirtualFieldMappingsBuilder;
import io.opentelemetry.javaagent.tooling.muzzle.references.ClassRef;
import io.scalac.mesmer.otelextension.instrumentations.akka.persistence.AkkaPersistenceAgent;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@AutoService(InstrumentationModule.class)
public class MesmerAkkaPersistenceInstrumentationModule extends InstrumentationModule
    implements InstrumentationModuleMuzzle {
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
  public List<String> getAdditionalHelperClassNames() {
    return Arrays.asList(
        "io.scalac.mesmer.configuration.Config$",
        "io.scalac.mesmer.configuration.Config",
        "io.scalac.mesmer.otelextension.instrumentations.akka.persistence.Instruments",
        "io.scalac.mesmer.otelextension.instrumentations.akka.persistence.InstrumentsProvider$$anon$1",
        "io.scalac.mesmer.otelextension.instrumentations.akka.persistence.InstrumentsProvider",
        "io.scalac.mesmer.otelextension.instrumentations.akka.persistence.InstrumentsProvider$",
        "io.scalac.mesmer.otelextension.instrumentations.akka.persistence.impl.PersistenceContext",
        "io.scalac.mesmer.otelextension.instrumentations.akka.persistence.impl.PersistenceContextProvider",
        "io.scalac.mesmer.otelextension.instrumentations.akka.persistence.impl.TemplatingPersistenceContextProvider",
        "io.scalac.mesmer.otelextension.instrumentations.akka.persistence.impl.IdentityPersistenceContextProvider");
  }

  @Override
  public Map<String, ClassRef> getMuzzleReferences() {
    return Collections.emptyMap();
  }

  @Override
  public void registerMuzzleVirtualFields(VirtualFieldMappingsBuilder builder) {
    builder
        .register(
            "akka.actor.ActorContext",
            "io.scalac.mesmer.otelextension.instrumentations.akka.persistence.impl.PersistenceContext")
        .register(
            "akka.actor.ActorSystem",
            "io.scalac.mesmer.otelextension.instrumentations.akka.persistence.impl.PersistenceContextProvider");
  }

  @Override
  public List<String> getMuzzleHelperClassNames() {
    return Collections.emptyList();
  }
}
