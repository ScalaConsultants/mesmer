package io.scalac.mesmer.otelextension.akka;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.scalac.mesmer.otelextension.instrumentations.akka.http.Connections;
import io.scalac.mesmer.otelextension.instrumentations.akka.http.PathMatching;
import java.util.Arrays;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class MesmerAkkaHttpInstrumentationModule extends InstrumentationModule {

  @Override
  public int order() {
    return -1;
  }

  public MesmerAkkaHttpInstrumentationModule() {
    super("mesmer-akka-http");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {

    return Arrays.asList(
        PathMatching.asyncHandler(),
        PathMatching.uuidPathMatcher(),
        PathMatching.doublePathMatcher(),
        PathMatching.neutralPathMatcher(),
        PathMatching.slashPathMatcher(),
        PathMatching.pathEndMatcher(),
        PathMatching.segmentRoute(),
        PathMatching.mapMatchedMatching(),
        PathMatching.andThenMatchedMatching(),
        PathMatching.applyPathMatcher(),
        PathMatching.segmentPathMatcher(),
        PathMatching.numberPathMatcher(),
        PathMatching.remainingPathMatcher(),
        PathMatching.rawMatcher(),
        Connections.connections());
  }

  @Override
  public boolean isHelperClass(String className) {
    if (className.matches("io.scalac.mesmer.otelextension.instrumentations.akka.http.*")
        || className.matches("io.scalac.mesmer.instrumentation.http.impl.*")) {
      return true;
    } else {
      return super.isHelperClass(className);
    }
  }

  @Override
  public List<String> getAdditionalHelperClassNames() {
    return List.of(
        "io.scalac.mesmer.instrumentation.http.impl.RawPathPrefixInterceptor",
        "io.scalac.mesmer.otelextension.instrumentations.akka.http.RawPathPrefixImplementation",
        "io.scalac.mesmer.otelextension.instrumentations.akka.http.RawPathPrefixImplementation$");
  }
}
