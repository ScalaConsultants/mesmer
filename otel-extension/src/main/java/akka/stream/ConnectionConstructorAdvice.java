package akka.stream;

import akka.stream.impl.fusing.GraphInterpreter;
import io.opentelemetry.instrumentation.api.field.VirtualField;
import io.scalac.mesmer.otelextension.instrumentations.akka.stream.impl.ConnectionCounters;
import net.bytebuddy.asm.Advice;

public class ConnectionConstructorAdvice {

    @Advice.OnMethodExit
    public static void initCounters(@Advice.This GraphInterpreter.Connection self) {
        System.out.println("Created a connection");
        try {
            VirtualField.find(GraphInterpreter.Connection.class, ConnectionCounters.class).set(self, new ConnectionCounters());
        } catch(Exception ex) {
            ex.printStackTrace();
            System.out.println("Failed initializing counters for streams");
        }
    }
}
