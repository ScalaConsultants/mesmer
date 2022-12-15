package akka.actor.impl;

import akka.actor.ActorRef;
import io.scalac.mesmer.otelextension.instrumentations.akka.actor.extension.Mailbox;
import net.bytebuddy.asm.Advice;

public class LocalActorMailboxAdvice {

  @Advice.OnMethodExit
  public static void actorOfExit(@Advice.Return ActorRef ref) {

    // TODO: Autocloseable???
    Mailbox.initMailboxSizeGauge(ref);
  }
}
