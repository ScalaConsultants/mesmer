package io.scalac.mesmer.instrumentation.http.impl;

import akka.http.scaladsl.server.PathMatcher;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import net.bytebuddy.asm.Advice;

public class AndThenMatchedMatchingAdvice {

  @Advice.OnMethodExit
  public static void onExit(
      @Advice.This PathMatcher.Matching<?> self, @Advice.Return PathMatcher.Matching<?> result) {

    String value = VirtualField.find(PathMatcher.Matching.class, String.class).get(self);

    if (value != null) {

      String innerValue = VirtualField.find(PathMatcher.Matching.class, String.class).get(result);
      if (innerValue != null) {

        VirtualField.find(PathMatcher.Matching.class, String.class).set(result, value + innerValue);
      } else {
        VirtualField.find(PathMatcher.Matching.class, String.class).set(result, value);
      }
    }
  }
}
