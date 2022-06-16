package akka.actor.impl.typed;

import akka.actor.ActorContext;
import akka.actor.ActorSystem;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.api.field.VirtualField;
import io.scalac.mesmer.otelextension.instrumentations.akka.actor.Instruments;
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
   * failure handle and some will recover from it and we should increase actor_failed metric in both
   * cases.
   */
  @Advice.OnMethodExit(onThrowable = Throwable.class)
  public static void enter(@Advice.This ActorContext self) {
    ActorCellInstrumentationState state =
        VirtualField.find(ActorContext.class, ActorCellInstrumentationState.class).get(self);
    Attributes attributes = VirtualField.find(ActorContext.class, Attributes.class).get(self);
    Instruments instruments =
        VirtualField.find(ActorSystem.class, Instruments.class).get(self.system());

    if (Objects.nonNull(instruments) && Objects.nonNull(state) && Objects.nonNull(attributes)) {
      state.setFailed();
      instruments.failedMessages().add(1, attributes);
    } else {
      // this should never happen
      self.system().log().warning("Couldn't find mesmer actor cell state inside ActorCell");
    }
  }
}
