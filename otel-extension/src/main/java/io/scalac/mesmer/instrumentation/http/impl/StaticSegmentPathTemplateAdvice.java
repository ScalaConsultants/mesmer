package io.scalac.mesmer.instrumentation.http.impl;

import akka.http.scaladsl.server.PathMatcher;
import io.opentelemetry.instrumentation.api.field.VirtualField;
import net.bytebuddy.asm.Advice;

public class StaticSegmentPathTemplateAdvice {

  @Advice.OnMethodExit
  public static void onExit(
      @Advice.Argument(0) String segment, @Advice.Return PathMatcher<?> result) {

    VirtualField.find(PathMatcher.class, String.class).set(result, segment);
  }
}
