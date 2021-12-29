package io.scalac.mesmer.otel_agent_extension;

import net.bytebuddy.asm.Advice;


// This is java because we need it to be (bytebuddy doesn't understand scala advice templates).
public class OtelAdvice {

    @Advice.OnMethodEnter
    public static void onEnter() throws InterruptedException {
        System.out.println("I was there and I was INJECTED by OTEL AGENT EXTENSION");
    }
}
