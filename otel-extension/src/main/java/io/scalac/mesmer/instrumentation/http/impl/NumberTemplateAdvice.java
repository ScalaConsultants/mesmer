package io.scalac.mesmer.instrumentation.http.impl;

import akka.http.scaladsl.server.PathMatcher;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import net.bytebuddy.asm.Advice;

public class NumberTemplateAdvice {

  @Advice.OnMethodExit
  public static void onExit(@Advice.This PathMatcher<?> self) {
    VirtualField.find(PathMatcher.class, String.class).set(self, "<number>");
  }
}
