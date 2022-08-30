package io.scalac.mesmer.otelextension.akka;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.tooling.muzzle.InstrumentationModuleMuzzle;
import io.opentelemetry.javaagent.tooling.muzzle.VirtualFieldMappingsBuilder;
import io.opentelemetry.javaagent.tooling.muzzle.references.ClassRef;
import io.scalac.mesmer.otelextension.instrumentations.akka.actor.AkkaActorAgent;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@AutoService(InstrumentationModule.class)
public class MesmerAkkaActorInstrumentationModule extends InstrumentationModule
    implements InstrumentationModuleMuzzle {
  public MesmerAkkaActorInstrumentationModule() {
    super("mesmer-akka-actor");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return Arrays.asList(
        AkkaActorAgent.actorSystemConfig(),
        AkkaActorAgent.actorMetricsExtension(),
        AkkaActorAgent.actorCellInit(),
        AkkaActorAgent.dispatchSendMessage(),
        AkkaActorAgent.mailboxDequeue(),
        AkkaActorAgent.classicStashSupportStashAdvice(),
        AkkaActorAgent.classicStashSupportPrependAdvice(),
        AkkaActorAgent.typedStashBufferAdvice(),
        AkkaActorAgent.typedAbstractSupervisorHandleReceiveExceptionAdvice(),
        AkkaActorAgent.actorUnhandledAdvice(),
        AkkaActorAgent.abstractBoundedNodeQueueAdvice(),
        AkkaActorAgent.boundedQueueBasedMessageQueueConstructorAdvice(),
        AkkaActorAgent.boundedQueueBasedMessageQueueQueueAdvice(),
        AkkaActorAgent.boundedMessageQueueSemanticsEnqueueAdvice(),
        AkkaActorAgent.actorCellReceived(),
        AkkaActorAgent.actorCreatedAdvice());
  }

  @Override
  public Map<String, ClassRef> getMuzzleReferences() {
    return Collections.emptyMap();
  }

  @Override
  public void registerMuzzleVirtualFields(VirtualFieldMappingsBuilder builder) {
    builder
        .register(
            "akka.dispatch.Envelope",
            "io.scalac.mesmer.otelextension.instrumentations.akka.actor.util.EnvelopeContext")
        .register(
            "akka.actor.ActorContext",
            "io.scalac.mesmer.otelextension.instrumentations.akka.actor.impl.otel.ActorCellInstrumentationState")
        .register(
            "akka.actor.ActorContext",
            "io.opentelemetry.javaagent.shaded.io.opentelemetry.api.common.Attributes")
        .register("akka.actor.ActorSystem", "io.scalac.mesmer.core.actor.ActorRefAttributeFactory")
        .register(
            "akka.dispatch.BoundedQueueBasedMessageQueue", "java.util.concurrent.BlockingQueue")
        .register("akka.dispatch.AbstractBoundedNodeQueue", "java.lang.Boolean");
  }

  @Override
  public List<String> getMuzzleHelperClassNames() {
    return Collections.emptyList();
  }

  @Override
  public List<String> getAdditionalHelperClassNames() {
    return Arrays.asList(
        "io.scalac.mesmer.core.actor.ActorRefAttributeFactory",
        "io.scalac.mesmer.core.actor.ConfiguredAttributeFactory",
        "io.scalac.mesmer.otelextension.instrumentations.akka.actor.Instruments$$anon$1",
        "io.scalac.mesmer.otelextension.instrumentations.akka.actor.Instruments",
        "io.scalac.mesmer.otelextension.instrumentations.akka.actor.Instruments$",
        "io.scalac.mesmer.otelextension.instrumentations.akka.actor.InstrumentsProvider",
        "io.scalac.mesmer.otelextension.instrumentations.akka.actor.InstrumentsProvider$",
        "io.scalac.mesmer.otelextension.instrumentations.akka.actor.util.EnvelopeContext",
        "io.scalac.mesmer.otelextension.instrumentations.akka.actor.util.EnvelopeContext$",
        "io.scalac.mesmer.otelextension.instrumentations.akka.actor.impl.otel.ActorCellInstrumentationState",
        "akka.actor.ProxiedQueue",
        "akka.actor.BoundedQueueProxy",
        "io.scalac.mesmer.otelextension.instrumentations.akka.actor.extension.ActorLifecycleEvents",
        "io.scalac.mesmer.otelextension.instrumentations.akka.actor.extension.ActorLifecycleEvents$ActorCreated",
        "io.scalac.mesmer.otelextension.instrumentations.akka.actor.extension.ActorLifecycleEvents$ActorTerminated",
        "io.scalac.mesmer.otelextension.instrumentations.akka.actor.extension.ActorLifecycleMetricsMonitor",
        "io.scalac.mesmer.otelextension.instrumentations.akka.actor.extension.ActorLifecycleMetricsMonitor$");
  }
}
