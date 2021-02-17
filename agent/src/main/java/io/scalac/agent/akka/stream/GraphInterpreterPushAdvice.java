package akka;

import net.bytebuddy.asm.Advice;

public class GraphInterpreterPushAdvice {

    @Advice.OnMethodEnter
    public static void onPush(
            @Advice.FieldValue(value = "pushCounter", readOnly = false) int counter
    ) {
        counter += 1;
    }
}
