package io.scalac.mesmer.otelextension;

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
        AkkaActorAgent.otel$.MODULE$.actorSystemConfig(),
        AkkaActorAgent.otel$.MODULE$.actorCellInit(),
        AkkaActorAgent.otel$.MODULE$.dispatchSendMessage(),
        AkkaActorAgent.otel$.MODULE$.mailboxDequeue(),
        AkkaActorAgent.otel$.MODULE$.classicStashSupportStashAdvice(),
        AkkaActorAgent.otel$.MODULE$.classicStashSupportPrependAdvice(),
        AkkaActorAgent.otel$.MODULE$.typedStashBufferAdvice(),
        AkkaActorAgent.otel$.MODULE$.typedAbstractSupervisorHandleReceiveExceptionAdvice(),
        AkkaActorAgent.otel$.MODULE$.actorUnhandledAdvice(),
        AkkaActorAgent.otel$.MODULE$.abstractBoundedNodeQueueAdvice(),
        AkkaActorAgent.otel$.MODULE$.boundedQueueBasedMessageQueueConstructorAdvice(),
        AkkaActorAgent.otel$.MODULE$.boundedQueueBasedMessageQueueQueueAdvice(),
        AkkaActorAgent.otel$.MODULE$.boundedMessageQueueSemanticsEnqueueAdvice(),
        AkkaActorAgent.otel$.MODULE$.actorCellReceived());
  }

  @Override
  public Map<String, ClassRef> getMuzzleReferences() {
    return Collections.emptyMap();
  }

  @Override
  public void registerMuzzleVirtualFields(VirtualFieldMappingsBuilder builder) {
    builder
        .register("akka.dispatch.Envelope", "io.scalac.mesmer.core.util.Timestamp")
        .register(
            "akka.dispatch.Envelope",
            "io.scalac.mesmer.otelextension.instrumentations.akka.actor.util.EnvelopeContext")
        .register("akka.actor.ActorContext", "io.scalac.mesmer.core.actor.ActorCellMetrics")
        .register(
            "akka.actor.ActorContext",
            "io.scalac.mesmer.otelextension.instrumentations.akka.actor.impl.otel.ActorCellInstrumentationState")
        .register(
            "akka.actor.ActorContext",
            "io.opentelemetry.javaagent.shaded.io.opentelemetry.api.common.Attributes")
        .register(
            "akka.actor.ActorSystem",
            "io.scalac.mesmer.otelextension.instrumentations.akka.actor.Instruments")
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
        "io.scalac.mesmer.otelextension.instrumentations.akka.actor.impl.ActorCellDroppedMessagesAdvice$",
        "io.scalac.mesmer.otelextension.instrumentations.akka.actor.impl.ActorCellReceiveMessageInstrumentation$",
        "io.scalac.mesmer.otelextension.instrumentations.akka.actor.impl.ActorCellSendMessageMetricInstrumentation$",
        "io.scalac.mesmer.otelextension.instrumentations.akka.actor.impl.ActorCellSendMessageTimestampInstrumentation$",
        "io.scalac.mesmer.otelextension.instrumentations.akka.actor.impl.ActorMetricsInitAdvice$",
        "io.scalac.mesmer.otelextension.instrumentations.akka.actor.impl.ActorUnhandledInstrumentation$",
        "io.scalac.mesmer.otelextension.instrumentations.akka.actor.impl.ClassicActorContextProviderOps$",
        "io.scalac.mesmer.otelextension.instrumentations.akka.actor.impl.ClassicActorOps$",
        "io.scalac.mesmer.otelextension.instrumentations.akka.actor.impl.StashConstructorAdvice$",
        "io.scalac.mesmer.otelextension.instrumentations.akka.actor.impl.StashGetters",
        "io.scalac.mesmer.otelextension.instrumentations.akka.actor.impl.ClassicStashInstrumentationStash$",
        "io.scalac.mesmer.otelextension.instrumentations.akka.actor.impl.ClassicStashInstrumentationPrepend$",
        "io.scalac.mesmer.otelextension.instrumentations.akka.actor.impl.EnvelopeOps$",
        "io.scalac.mesmer.otelextension.instrumentations.akka.actor.impl.LocalActorRefProviderAdvice$",
        "io.scalac.mesmer.otelextension.instrumentations.akka.actor.impl.MailboxDequeueInstrumentation$",
        "io.scalac.mesmer.otelextension.instrumentations.akka.actor.impl.MailboxOps$",
        "io.scalac.mesmer.otelextension.instrumentations.akka.actor.impl.StashBufferAdvice$",
        "io.scalac.mesmer.otelextension.instrumentations.akka.actor.impl.SupervisorHandleReceiveExceptionInstrumentation$",
        "io.scalac.mesmer.otelextension.instrumentations.akka.actor.BoundedNodeMessageQueueAdvice$",
        "io.scalac.mesmer.otelextension.instrumentations.akka.actor.AbstractBoundedNodeQueueAdvice$",
        "io.scalac.mesmer.otelextension.instrumentations.akka.actor.AkkaMailboxInstrumentations",
        "io.scalac.mesmer.otelextension.instrumentations.akka.actor.EnvelopeDecorator$",
        "io.scalac.mesmer.instrumentation.actor.impl.BoundedQueueBasedMessageQueueAdvice",
        "io.scalac.mesmer.core.actor.ActorCellDecorator",
        "io.scalac.mesmer.core.actor.ActorRefConfiguration",
        "io.scalac.mesmer.core.actor.DefaultActorRefConfiguration",
        "io.scalac.mesmer.core.actor.DefaultActorRefConfiguration$",
        "io.scalac.mesmer.core.actor.WithSystemActorRefConfigurator",
        "io.scalac.mesmer.otelextension.instrumentations.akka.actor.Instruments",
        "io.scalac.mesmer.otelextension.instrumentations.akka.actor.util.EnvelopeContext",
        "io.scalac.mesmer.otelextension.instrumentations.akka.actor.util.EnvelopeContext$",
        "io.scalac.mesmer.otelextension.instrumentations.akka.actor.impl.otel.ActorCellInstrumentationState",
        "akka.actor.ProxiedQueue",
        "akka.actor.BoundedQueueProxy");
  }
}
