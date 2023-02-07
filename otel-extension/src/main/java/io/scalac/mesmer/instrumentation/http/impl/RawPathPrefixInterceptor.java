package io.scalac.mesmer.instrumentation.http.impl;

import akka.http.scaladsl.server.Directive;
import akka.http.scaladsl.server.PathMatcher;
import io.scalac.mesmer.otelextension.instrumentations.akka.http.RawPathPrefixImplementation;
import net.bytebuddy.asm.Advice;

public class RawPathPrefixInterceptor {

  public static Directive<?> rawPathPrefix(@Advice.Argument(0) PathMatcher<?> matcher) {
    return RawPathPrefixImplementation.rawPathPrefix(matcher);
  }
}
