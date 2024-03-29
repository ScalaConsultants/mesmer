package io.scalac.mesmer.otelextension.akka;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.tooling.muzzle.InstrumentationModuleMuzzle;
import io.opentelemetry.javaagent.tooling.muzzle.VirtualFieldMappingsBuilder;
import io.opentelemetry.javaagent.tooling.muzzle.references.ClassRef;
import io.scalac.mesmer.otelextension.instrumentations.akka.stream.AkkaStreamAgents;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static io.scalac.mesmer.utils.Combine.combine;

@AutoService(InstrumentationModule.class)
public class MesmerAkkaStreamInstrumentationModule extends InstrumentationModule
    implements InstrumentationModuleMuzzle {

  public MesmerAkkaStreamInstrumentationModule() {
    super("mesmer-akka-stream");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return AkkaStreamAgents.getAllStreamInstrumentations();
  }

  @Override
  public Map<String, ClassRef> getMuzzleReferences() {
    return Collections.emptyMap();
  }

  @Override
  public void registerMuzzleVirtualFields(VirtualFieldMappingsBuilder builder) {
    builder.register(
        "akka.stream.impl.fusing.GraphInterpreter$Connection",
        "io.scalac.mesmer.otelextension.instrumentations.akka.stream.impl.ConnectionCounters");
  }

  @Override
  public List<String> getMuzzleHelperClassNames() {
    return Collections.emptyList();
  }

  @Override
  public List<String> getAdditionalHelperClassNames() {
    return combine(
        MesmerAkkaHelpers.coreHelpers(),
        Arrays.asList(
            "io.scalac.mesmer.otelextension.instrumentations.akka.stream.impl.ConnectionCounters",
            "akka.stream.GraphInterpreterOtelPushAdvice$",
            "akka.stream.GraphInterpreterOtelPullAdvice$",
            "akka.stream.GraphLogicOtelOps",
            "akka.stream.GraphLogicOtelOps$",
            "akka.stream.GraphLogicOtelOps$GraphLogicEnh",
            "akka.stream.GraphLogicOtelOps$GraphLogicEnh$",
            "akka.ConnectionOtelOps",
            "akka.ConnectionOtelOps$",
            "io.scalac.mesmer.otelextension.instrumentations.akka.stream.StreamService$",
            "io.scalac.mesmer.otelextension.instrumentations.akka.stream.ActorEvent",
            "io.scalac.mesmer.otelextension.instrumentations.akka.stream.ActorEvent$",
            "io.scalac.mesmer.otelextension.instrumentations.akka.stream.ActorEvent$TagsSet",
            "io.scalac.mesmer.otelextension.instrumentations.akka.stream.StreamEvent",
            "io.scalac.mesmer.otelextension.instrumentations.akka.stream.StreamEvent$",
            "io.scalac.mesmer.otelextension.instrumentations.akka.stream.StreamEvent$StreamInterpreterStats",
            "io.scalac.mesmer.otelextension.instrumentations.akka.stream.StreamEvent$LastStreamStats",
            "io.scalac.mesmer.otelextension.instrumentations.akka.stream.stream$",
            "io.scalac.mesmer.otelextension.instrumentations.akka.stream.impl.ActorGraphInterpreterOtelDecorator",
            "io.scalac.mesmer.otelextension.instrumentations.akka.stream.impl.ActorGraphInterpreterOtelDecorator$",
            "io.scalac.mesmer.otelextension.instrumentations.akka.stream.impl.ActorGraphInterpreterOtelDecorator$$anonfun$addCollectionReceive$1",
            "io.scalac.mesmer.otelextension.instrumentations.akka.stream.impl.GraphStageIslandOps",
            "io.scalac.mesmer.otelextension.instrumentations.akka.stream.impl.GraphStageIslandOps$",
            "io.scalac.mesmer.otelextension.instrumentations.akka.stream.impl.GraphStageIslandOps$TerminalSink$",
            "io.scalac.mesmer.otelextension.instrumentations.akka.stream.AkkaStreamMonitorExtension",
            "io.scalac.mesmer.otelextension.instrumentations.akka.stream.AkkaStreamMonitorExtension$$anon$1",
            "io.scalac.mesmer.otelextension.instrumentations.akka.stream.AkkaStreamMonitorExtensionId$",
            "io.scalac.mesmer.otelextension.instrumentations.akka.stream.AkkaStreamMonitorExtension$",
            "io.scalac.mesmer.otelextension.instrumentations.akka.stream.AkkaStreamMonitorExtension$StreamStatsReceived",
            "io.scalac.mesmer.otelextension.instrumentations.akka.stream.AkkaStreamMetrics",
            "io.scalac.mesmer.otelextension.instrumentations.akka.stream.AkkaStreamConfig$",
            "io.scalac.mesmer.otelextension.instrumentations.akka.stream.AkkaStreamAttributes$",
            "io.scalac.mesmer.otelextension.instrumentations.akka.stream.CachingConfig",
            "io.scalac.mesmer.otelextension.instrumentations.akka.stream.CachingConfig$",
            "io.scalac.mesmer.otelextension.instrumentations.akka.stream.StreamSnapshotsService",
            "io.scalac.mesmer.otelextension.instrumentations.akka.stream.StreamSnapshotsService$",
            "io.scalac.mesmer.otelextension.instrumentations.akka.stream.StreamSnapshotsService$$anon$1",
            "io.scalac.mesmer.otelextension.instrumentations.akka.stream.AkkaStreamMonitorExtension$$anonfun$collectStageSnapshots$1",
            "io.scalac.mesmer.otelextension.instrumentations.akka.stream.AkkaStreamMonitorExtension$StageSnapshot",
            "io.scalac.mesmer.otelextension.instrumentations.akka.stream.AkkaStreamMonitorExtension$$anonfun$getPerStageValues$1"));
  }
}
