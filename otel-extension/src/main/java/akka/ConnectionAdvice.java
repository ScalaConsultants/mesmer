package akka;

import akka.stream.impl.fusing.GraphInterpreter.Connection;
import io.opentelemetry.instrumentation.api.field.VirtualField;
import net.bytebuddy.asm.Advice;

public class ConnectionAdvice {

    @Advice.OnMethodExit
    public static void onEnter(@Advice.This Connection self) {
        System.out.println("Connection created");
        VirtualField<Connection, Integer> vf = VirtualField.find(Connection.class, Integer.class);
        vf.set(self, 0);
    }
}
