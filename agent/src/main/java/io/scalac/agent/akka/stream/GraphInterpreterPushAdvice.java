package akka;

import net.bytebuddy.asm.Advice;

public class GraphInterpreterPushAdvice {

    @Advice.OnMethodEnter
    public static void onPush(
            @Advice.FieldValue(value = "pushCounter", readOnly = false) int counter
    ) {
        counter += 1;
        if (counter % 10 == 0) {
            System.out.println("Pushed next 10");
        }
    }
}
