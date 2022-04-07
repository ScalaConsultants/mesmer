package akka.actor;

import static net.bytebuddy.asm.Advice.*;

import akka.dispatch.Envelope;
import java.util.concurrent.BlockingQueue;

public class ProxiedQueue {
  public static final String queueFieldName = "_proxiedQueue";

  @OnMethodExit
  public static void constructor(
      @FieldValue(value = queueFieldName, readOnly = false) BlockingQueue<Envelope> queue,
      @This BlockingQueue<Envelope> self) {
    queue = new BoundedQueueProxy<>(self);
  }
}
