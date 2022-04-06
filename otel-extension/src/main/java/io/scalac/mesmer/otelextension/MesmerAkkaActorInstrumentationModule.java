package io.scalac.mesmer.otelextension;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.tooling.muzzle.InstrumentationModuleMuzzle;
import io.opentelemetry.javaagent.tooling.muzzle.VirtualFieldMappingsBuilder;
import io.opentelemetry.javaagent.tooling.muzzle.references.ClassRef;
import io.scalac.mesmer.agent.akka.actor.AkkaActorAgent;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@AutoService(InstrumentationModule.class)
public class MesmerAkkaActorInstrumentationModule extends InstrumentationModule implements InstrumentationModuleMuzzle {
    public MesmerAkkaActorInstrumentationModule() {
        super("mesmer-akka-actor");
    }

    @Override
    public List<TypeInstrumentation> typeInstrumentations() {
        return AkkaActorAgent.agent().asOtelTypeInstrumentations();
    }

    @Override
    public List<String> getAdditionalHelperClassNames() {
        return Arrays.asList(
                "io.scalac.mesmer.agent.akka.actor.impl.StashGetters",
                "io.scalac.mesmer.agent.akka.actor.impl.EnvelopeOps$",
                "io.scalac.mesmer.agent.akka.actor.AbstractBoundedNodeQueueAdvice",
                "io.scalac.mesmer.agent.akka.actor.impl.ClassicStashInstrumentationPrepend$",
                "io.scalac.mesmer.agent.akka.actor.impl.SupervisorHandleReceiveExceptionInstrumentation$",
                "io.scalac.mesmer.agent.akka.actor.impl.ClassicActorOps$",
                "io.scalac.mesmer.agent.akka.actor.impl.EnvelopeOps",
                "io.scalac.mesmer.agent.akka.actor.impl.ClassicStashInstrumentationStash",
                "io.scalac.mesmer.agent.akka.actor.impl.ActorCellReceiveMessageInstrumentation$",
                "io.scalac.mesmer.agent.akka.actor.impl.ActorCellReceiveMessageInstrumentation",
                "io.scalac.mesmer.agent.akka.actor.impl.ActorMetricsInitAdvice$",
                "io.scalac.mesmer.agent.akka.actor.impl.ClassicActorContextProviderOps$",
                "io.scalac.mesmer.agent.akka.actor.impl.LocalActorRefProviderAdvice$",
                "io.scalac.mesmer.agent.akka.actor.impl.ClassicActorContextProviderOps",
                "io.scalac.mesmer.agent.akka.actor.impl.MailboxDequeueInstrumentation$",
                "io.scalac.mesmer.agent.akka.actor.impl.MailboxOps",
                "io.scalac.mesmer.agent.akka.actor.impl.ActorCellSendMessageTimestampInstrumentation$",
                "io.scalac.mesmer.agent.akka.actor.impl.ActorCellSendMessageMetricInstrumentation$",
                "io.scalac.mesmer.agent.akka.actor.impl.SupervisorHandleReceiveExceptionInstrumentation",
                "io.scalac.mesmer.agent.akka.actor.impl.StashConstructorAdvice$",
                "io.scalac.mesmer.agent.akka.actor.impl.ClassicStashInstrumentationStash$",
                "io.scalac.mesmer.agent.akka.actor.impl.ClassicStashInstrumentationPrepend",
                "io.scalac.mesmer.agent.akka.actor.impl.ActorCellDroppedMessagesAdvice$",
                "io.scalac.mesmer.agent.akka.actor.impl.ClassicActorOps",
                "io.scalac.mesmer.agent.akka.actor.impl.ActorUnhandledInstrumentation$",
                "io.scalac.mesmer.agent.akka.actor.impl.ActorCellSendMessageTimestampInstrumentation",
                "io.scalac.mesmer.agent.akka.actor.impl.MailboxDequeueInstrumentation",
                "io.scalac.mesmer.agent.akka.actor.impl.ActorUnhandledInstrumentation",
                "io.scalac.mesmer.agent.akka.actor.impl.ActorCellSendMessageMetricInstrumentation",
                "io.scalac.mesmer.agent.akka.actor.impl.MailboxOps$",
                "io.scalac.mesmer.agent.akka.actor.impl.StashBufferAdvice$",
                "io.scalac.mesmer.agent.akka.actor.AkkaActorAgent",
                "io.scalac.mesmer.agent.akka.actor.LastEnqueueResult",
                "io.scalac.mesmer.agent.akka.actor.EnvelopeDecorator",
                "io.scalac.mesmer.agent.akka.actor.BoundedBlockingQueueDecorator",
                "io.scalac.mesmer.agent.akka.actor.AbstractBoundedQueueDecorator",
                "io.scalac.mesmer.agent.akka.actor.ProxiedQueue",
                "io.scalac.mesmer.agent.akka.actor.LinkedBlockingQueueDecorator$",
                "io.scalac.mesmer.agent.akka.actor.AbstractBoundedQueueDecorator$",
                "io.scalac.mesmer.agent.akka.actor.LastEnqueueResult$",
                "io.scalac.mesmer.agent.akka.actor.LinkedBlockingQueueDecorator",
                "io.scalac.mesmer.agent.akka.actor.EnvelopeDecorator$",
                "io.scalac.mesmer.agent.akka.actor.AkkaMailboxInstrumentations",
                "io.scalac.mesmer.agent.akka.actor.BoundedNodeMessageQueueAdvice$",
                "io.scalac.mesmer.agent.akka.actor.BoundedBlockingQueueDecorator$",
                "io.scalac.mesmer.agent.akka.actor.BoundedQueueProxy",
                "io.scalac.mesmer.agent.akka.actor.AkkaActorAgent$"
        );
    }

    @Override
    public Map<String, ClassRef> getMuzzleReferences() {
        return Collections.emptyMap();
    }

    @Override
    public void registerMuzzleVirtualFields(VirtualFieldMappingsBuilder builder) {
        builder
                .register("akka.actor.ActorContext", "io.scalac.mesmer.core.actorActorCellMetrics")
                .register("akka.dispatch.Envelope", "io.scalac.mesmer.core.util.Timestamp");
    }

    @Override
    public List<String> getMuzzleHelperClassNames() {
        return Collections.emptyList();
    }
}
