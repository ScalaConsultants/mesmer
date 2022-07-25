package akka.actor.impl.typed;

import akka.actor.ActorContext;
import akka.actor.typed.TypedActorContext;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.api.field.VirtualField;
import io.scalac.mesmer.otelextension.instrumentations.akka.actor.Instruments;
import io.scalac.mesmer.otelextension.instrumentations.akka.actor.InstrumentsProvider;
import io.scalac.mesmer.otelextension.instrumentations.akka.actor.impl.otel.ActorCellInstrumentationState;
import java.util.Objects;
import net.bytebuddy.asm.Advice;

public class SupervisorHandleReceiveExceptionAdvice {

  /**
   * We need another instrumentation here as typed Actors introduced another level of supervision -
   * implemented at [[ akka.actor.typed.internal.InterceptorImpl ]] level instead of class
   * supervision model. To make actors_failed metric consistent we must prevent increasing this
   * metric by instrumentation code in [[ ActorCellReceivedAdvice ]] by setting up a failed flag to
   * true - some [[ akka.actor.typed.internal.AbstractSupervisor ]] might fall back to classic
   * failure handle and some will recover from it and we should increase
   * mesmer_akka_failed_messages_total metric in both cases.
   */
  @Advice.OnMethodExit(onThrowable = Throwable.class)
  public static void exit(@Advice.Argument(0) TypedActorContext<?> typedContext) {
    ActorContext classicContext = typedContext.asScala().classicActorContext();
    ActorCellInstrumentationState state =
        VirtualField.find(ActorContext.class, ActorCellInstrumentationState.class)
            .get(classicContext);
    Attributes attributes =
        VirtualField.find(ActorContext.class, Attributes.class).get(classicContext);
    Instruments instruments = InstrumentsProvider.instance();

    if (Objects.nonNull(state) && Objects.nonNull(attributes)) {
      state.setFailed();
      instruments.failedMessages().add(1L, attributes);
    } else {
      // this should never happen
      classicContext
          .system()
          .log()
          .warning("Couldn't find mesmer actor cell state inside ActorCell");
    }
  }
}
