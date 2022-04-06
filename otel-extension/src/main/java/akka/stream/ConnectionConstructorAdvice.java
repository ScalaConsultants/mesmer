package akka.stream;

import akka.stream.impl.fusing.GraphInterpreter;
import io.opentelemetry.instrumentation.api.field.VirtualField;
import io.scalac.mesmer.otelextension.instrumentations.akka.stream.impl.ConnectionCounters;
import net.bytebuddy.asm.Advice;

public class ConnectionConstructorAdvice {

  // See the summary explaining why the "Object" argument type is used:
  // https://github.com/ScalaConsultants/mesmer/pull/361
  @Advice.OnMethodExit
  public static void initCounters(@Advice.This Object self) {
    VirtualField.find(GraphInterpreter.Connection.class, ConnectionCounters.class)
        .set((GraphInterpreter.Connection) self, new ConnectionCounters());
  }
}
