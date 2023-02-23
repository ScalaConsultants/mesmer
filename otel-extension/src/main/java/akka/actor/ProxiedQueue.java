package akka.actor;

import static net.bytebuddy.asm.Advice.*;

import akka.dispatch.BoundedQueueBasedMessageQueue;
import akka.dispatch.Envelope;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.util.concurrent.BlockingQueue;

public class ProxiedQueue {

  @OnMethodExit
  public static void constructor(@This BlockingQueue<Envelope> self) {
    VirtualField.find(BoundedQueueBasedMessageQueue.class, BlockingQueue.class)
        .set((BoundedQueueBasedMessageQueue) self, new BoundedQueueProxy<>(self));
  }
}
