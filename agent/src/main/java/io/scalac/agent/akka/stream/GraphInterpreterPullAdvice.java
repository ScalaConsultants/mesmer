package akka;

import io.scalac.agent.akka.stream.ConnectionOps;
import net.bytebuddy.asm.Advice;

public class GraphInterpreterPullAdvice {

    @Advice.OnMethodEnter
    public static void onPush(
            @Advice.Argument(0) Object currentConnection
    ) {
        ConnectionOps.incrementPullCounter(currentConnection);
    }
}
