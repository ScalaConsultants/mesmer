package akka.stream;

import akka.stream.impl.fusing.GraphInterpreter;
import io.opentelemetry.instrumentation.api.field.VirtualField;
import net.bytebuddy.asm.Advice;
import io.scalac.mesmer.agentcopy.akka.stream.impl.ConnectionCounters;
import scala.Tuple2;

public class ConnectionConstructorAdvice {

    @Advice.OnMethodExit
    public static void initCounters(@Advice.This GraphInterpreter.Connection self) {
        VirtualField.find(GraphInterpreter.Connection.class, ConnectionCounters.class).set(self, new ConnectionCounters());
    }
}
