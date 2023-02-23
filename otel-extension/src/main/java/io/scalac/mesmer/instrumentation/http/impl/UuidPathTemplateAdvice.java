package io.scalac.mesmer.instrumentation.http.impl;

import akka.http.scaladsl.server.PathMatcher;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import net.bytebuddy.asm.Advice;

public class UuidPathTemplateAdvice {

  @Advice.OnMethodEnter
  public static void onEnter(@Advice.Argument(value = 0, readOnly = false) PathMatcher<?> result) {

    VirtualField.find(PathMatcher.class, String.class).set(result, "<uuid>");
  }
}
