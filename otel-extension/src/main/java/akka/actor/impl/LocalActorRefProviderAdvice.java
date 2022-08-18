package akka.actor.impl;

import io.scalac.mesmer.otelextension.instrumentations.akka.actor.InstrumentsProvider;
import net.bytebuddy.asm.Advice;

public class LocalActorRefProviderAdvice {

  @Advice.OnMethodExit
  public static void actorOfExit() {
    InstrumentsProvider.instance().actorsCreated().add(1);
  }
}
