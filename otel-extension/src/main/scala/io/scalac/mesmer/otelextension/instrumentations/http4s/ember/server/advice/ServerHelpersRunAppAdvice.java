package io.scalac.mesmer.otelextension.instrumentations.http4s.ember.server.advice;

import cats.data.Kleisli;
import net.bytebuddy.asm.Advice;

public class ServerHelpersRunAppAdvice {

    @Advice.OnMethodEnter
    public static void runAppEnter(@Advice.Argument(value = 4, readOnly = false) Kleisli<?, ?, ?> httpApp) {
        httpApp = ServerHelpersRunAppAdviceHelper.withMetrics(httpApp);
    }
}
