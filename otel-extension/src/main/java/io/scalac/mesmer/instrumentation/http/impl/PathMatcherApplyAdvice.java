package io.scalac.mesmer.instrumentation.http.impl;

import akka.http.scaladsl.server.PathMatcher;
import io.opentelemetry.instrumentation.api.field.VirtualField;
import net.bytebuddy.asm.Advice;

public class PathMatcherApplyAdvice {

  @Advice.OnMethodExit
  public static void onExit(
      @Advice.This PathMatcher<?> self, @Advice.Return PathMatcher.Matching<?> result) {

    String template = VirtualField.find(PathMatcher.class, String.class).get(self);

    String matchingResult = VirtualField.find(PathMatcher.Matching.class, String.class).get(result);

    if (template != null && matchingResult == null) {
      VirtualField.find(PathMatcher.Matching.class, String.class).set(result, template);
    }
  }
}
