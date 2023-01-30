package io.scalac.mesmer.instrumentation.http.impl;

import akka.http.scaladsl.server.PathMatcher;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import net.bytebuddy.asm.Advice;

public class MapMatchedMatchingAdvice {

  @Advice.OnMethodExit
  public static void onExit(
      @Advice.This PathMatcher.Matching<?> self, @Advice.Return PathMatcher.Matching<?> result) {

    String value = VirtualField.find(PathMatcher.Matching.class, String.class).get(self);
    if (value != null) {

      VirtualField.find(PathMatcher.Matching.class, String.class).set(result, value);
    }
  }
}
