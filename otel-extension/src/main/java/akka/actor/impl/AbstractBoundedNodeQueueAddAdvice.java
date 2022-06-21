package akka.actor.impl;

import akka.dispatch.AbstractBoundedNodeQueue;
import io.opentelemetry.instrumentation.api.field.VirtualField;
import net.bytebuddy.asm.Advice;

public class AbstractBoundedNodeQueueAddAdvice {

  @Advice.OnMethodExit
  public static void exit(
      @Advice.Return Boolean result, @Advice.This AbstractBoundedNodeQueue<?> self) {
    VirtualField.find(AbstractBoundedNodeQueue.class, Boolean.class).set(self, result);
  }
}
