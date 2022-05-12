package io.scalac.mesmer.instrumentation.http.impl;

import akka.http.scaladsl.server.PathMatcher;
import io.opentelemetry.instrumentation.api.field.VirtualField;
import net.bytebuddy.asm.Advice;

public class DoubleTemplateAdvice {

  @Advice.OnMethodEnter
  public static void onEnter(@Advice.Argument(value = 0) PathMatcher<?> result) {

    VirtualField.find(PathMatcher.class, String.class).set(result, "<double>");
  }
}
