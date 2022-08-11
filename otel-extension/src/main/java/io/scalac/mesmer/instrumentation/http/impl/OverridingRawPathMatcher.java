package io.scalac.mesmer.instrumentation.http.impl;

import akka.http.scaladsl.server.PathMatcher;
import io.scalac.mesmer.otelextension.instrumentations.akka.http.OverridingRawPatchMatcherImpl;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;

public class OverridingRawPathMatcher {

  @Advice.OnMethodExit
  public static void onExit(
      @Advice.Argument(0) Object matcher,
      @Advice.Return(readOnly = false, typing = Assigner.Typing.DYNAMIC) Object result) {
    //

    result = OverridingRawPatchMatcherImpl.rawPathPrefix((PathMatcher<?>) matcher);
  }
}
