package io.scalac.mesmer.instrumentation.http.impl;

import akka.http.scaladsl.server.Directive;
import akka.http.scaladsl.server.PathMatcher;
import io.scalac.mesmer.otelextension.instrumentations.akka.http.OverridingRawPatchMatcherImpl;
import net.bytebuddy.asm.Advice;

public class OverridingRawPathMatcher {

  @Advice.OnMethodExit
  public static void onExit(
      @Advice.Argument(0) PathMatcher<?> matcher,
      @Advice.Return(readOnly = false) Directive<?> result) {

    result = OverridingRawPatchMatcherImpl.rawPathPrefix(matcher);
  }
}
