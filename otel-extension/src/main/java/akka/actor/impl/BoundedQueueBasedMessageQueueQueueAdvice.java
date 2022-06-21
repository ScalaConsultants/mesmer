package akka.actor.impl;

import akka.dispatch.BoundedQueueBasedMessageQueue;
import io.opentelemetry.instrumentation.api.field.VirtualField;
import java.util.concurrent.BlockingQueue;
import net.bytebuddy.asm.Advice;

public class BoundedQueueBasedMessageQueueQueueAdvice {

  @Advice.OnMethodExit
  public static void queue(
      @Advice.Return(readOnly = false) BlockingQueue<?> result, @Advice.This Object self) {
    result =
        VirtualField.find(BoundedQueueBasedMessageQueue.class, BlockingQueue.class)
            .get((BoundedQueueBasedMessageQueue) self);
  }
}
