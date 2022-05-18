package io.scalac.mesmer.otelextension;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.tooling.muzzle.InstrumentationModuleMuzzle;
import io.opentelemetry.javaagent.tooling.muzzle.VirtualFieldMappingsBuilder;
import io.opentelemetry.javaagent.tooling.muzzle.references.ClassRef;
import io.scalac.mesmer.otelextension.instrumentations.akka.http.Connections;
import io.scalac.mesmer.otelextension.instrumentations.akka.http.PathMatching;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@AutoService(InstrumentationModule.class)
public class MesmerAkkaHttpInstrumentationModule extends InstrumentationModule
    implements InstrumentationModuleMuzzle {

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
  public List<String> getMuzzleHelperClassNames() {
    return emptyList();
  }

  @Override
  public Map<String, ClassRef> getMuzzleReferences() {
    return emptyMap();
  }

  @Override
  public void registerMuzzleVirtualFields(VirtualFieldMappingsBuilder builder) {
    builder.register("akka.http.scaladsl.server.PathMatcher", "java.lang.String");
    builder.register("akka.http.scaladsl.server.PathMatcher$Matching", "java.lang.String");
  }

  @Override
  public List<String> getAdditionalHelperClassNames() {
    return Arrays.asList(
        "io.scalac.mesmer.otelextension.instrumentations.akka.http.RouteContext$",
        "io.scalac.mesmer.otelextension.instrumentations.akka.http.RouteContext",
        "io.scalac.mesmer.otelextension.instrumentations.akka.http.RouteTemplateHolder",
        "io.scalac.mesmer.otelextension.instrumentations.akka.http.UpdateHttpRouteWrapper$$anonfun$$nestedInanonfun$apply$1$1",
        "io.scalac.mesmer.otelextension.instrumentations.akka.http.UpdateHttpRouteWrapper",
        "io.scalac.mesmer.otelextension.instrumentations.akka.http.OverridingRawPatchMatcherImpl$",
        "io.scalac.mesmer.otelextension.instrumentations.akka.http.OverridingRawPatchMatcherImpl",
        "io.scalac.mesmer.otelextension.instrumentations.akka.http.AkkaHttpConnectionsInstrumentation$",
        "io.scalac.mesmer.otelextension.instrumentations.akka.http.AkkaHttpConnectionsInstrumentation$HttpConnectionInstruments$",
        "io.scalac.mesmer.otelextension.instrumentations.akka.http.AkkaHttpConnectionsInstrumentation");
  }
}
