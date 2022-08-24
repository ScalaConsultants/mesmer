package akka.actor.impl;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import io.scalac.mesmer.otelextension.instrumentations.akka.actor.ActorEvent;
import net.bytebuddy.asm.Advice;

public class LocalActorRefProviderAdvice {

  @Advice.OnMethodExit
  public static void actorOfExit(
      @Advice.Argument(0) ActorSystem system, @Advice.Return ActorRef ref) {
    system.eventStream().publish(new ActorEvent.ActorCreated(ref));
  }
}
