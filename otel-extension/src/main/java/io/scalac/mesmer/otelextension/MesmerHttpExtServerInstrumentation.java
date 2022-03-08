/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.scalac.mesmer.otelextension;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import akka.http.scaladsl.model.HttpRequest;
import akka.http.scaladsl.model.HttpResponse;
import akka.stream.Materializer;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.scalac.mesmer.agent.akka.newhttp.AsyncWrapper;
import io.scalac.mesmer.agent.akka.newhttp.SyncWrapper;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import scala.Function1;
import scala.concurrent.Future;

public class MesmerHttpExtServerInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("akka.http.scaladsl.HttpExt");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("bindAndHandleSync").and(takesArgument(0, named("scala.Function1"))),
        this.getClass().getName() + "$AkkaHttpSyncAdvice");
    transformer.applyAdviceToMethod(
        named("bindAndHandleAsync").and(takesArgument(0, named("scala.Function1"))),
        this.getClass().getName() + "$AkkaHttpAsyncAdvice");
  }

  @SuppressWarnings("unused")
  public static class AkkaHttpSyncAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void wrapHandler(
        @Advice.Argument(value = 0, readOnly = false)
            Function1<HttpRequest, HttpResponse> handler) {
      handler = new SyncWrapper(handler);
    }
  }

  @SuppressWarnings("unused")
  public static class AkkaHttpAsyncAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void wrapHandler(
        @Advice.Argument(value = 0, readOnly = false)
            Function1<HttpRequest, Future<HttpResponse>> handler,
        @Advice.Argument(7) Materializer materializer) {
      handler = new AsyncWrapper(handler, materializer.executionContext());
    }
  }
}
