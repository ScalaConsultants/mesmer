package akka.stream;

import akka.stream.impl.fusing.GraphInterpreter;
import io.opentelemetry.instrumentation.api.field.VirtualField;
import net.bytebuddy.asm.Advice;
import scala.Tuple2;

public class ConnectionConstructorAdvice {

    @Advice.OnMethodExit
    public static void initCounters(@Advice.This GraphInterpreter.Connection self) {
        VirtualField.find(GraphInterpreter.Connection.class, Tuple2.class).set(self, Tuple2.apply(0L, 0L));
    }
}
