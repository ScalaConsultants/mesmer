package io.scalac.mesmer.otelextension.akka;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.scalac.mesmer.otelextension.instrumentations.akka.actor.AkkaActorAgent;
import java.util.Arrays;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class MesmerAkkaActorInstrumentationModule extends InstrumentationModule {
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
        AkkaActorAgent.mailboxEnqueue(),
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
  public boolean isHelperClass(String className) {
    if (className.matches("io.scalac.mesmer.otelextension.instrumentations.akka.actor.*")
        || className.matches("io.scalac.mesmer.core.*")
        || className.contains("akka.actor.BoundedQueueProxy")) {
      return true;
    } else {
      return super.isHelperClass(className);
    }
  }
}
