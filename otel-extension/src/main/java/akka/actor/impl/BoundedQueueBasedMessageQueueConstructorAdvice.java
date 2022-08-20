package akka.actor.impl;

import akka.actor.BoundedQueueProxy;
import akka.dispatch.BoundedQueueBasedMessageQueue;
import akka.dispatch.Envelope;
import io.opentelemetry.instrumentation.api.field.VirtualField;
import java.util.concurrent.BlockingQueue;
import net.bytebuddy.asm.Advice;

public class BoundedQueueBasedMessageQueueConstructorAdvice {

  @Advice.OnMethodExit
  public static void exit(@Advice.This BlockingQueue<Envelope> self) {
    VirtualField.find(BoundedQueueBasedMessageQueue.class, BlockingQueue.class)
        .set((BoundedQueueBasedMessageQueue) self, new BoundedQueueProxy<>(self));
  }
}
