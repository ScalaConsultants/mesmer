package akka.actor.impl;

import io.scalac.mesmer.core.actor.ActorCellMetrics;
import net.bytebuddy.asm.Advice;

public class ActorCellInitAdvice {

  @Advice.OnMethodEnter
  public static void initActorCell(
      @Advice.FieldValue(value = "_actorCellMetrics", readOnly = false) ActorCellMetrics metrics) {
    metrics = new ActorCellMetrics();
  }
}
