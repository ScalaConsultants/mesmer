package io.scalac.mesmer.otelextension.akka;

import static java.util.Collections.emptyList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.tooling.muzzle.InstrumentationModuleMuzzle;
import io.opentelemetry.javaagent.tooling.muzzle.VirtualFieldMappingsBuilder;
import io.opentelemetry.javaagent.tooling.muzzle.references.ClassRef;
import io.scalac.mesmer.otelextension.instrumentations.akka.cluster.AkkaClusterAgent;
import java.util.List;
import java.util.Map;

@AutoService(InstrumentationModule.class)
public class MesmerAkkaClusterInstrumentationModule extends InstrumentationModule
    implements InstrumentationModuleMuzzle {
  public MesmerAkkaClusterInstrumentationModule() {
    super("mesmer-akka-cluster");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return List.of(AkkaClusterAgent.clusterMetricsExtension());
  }

  @Override
  public Map<String, ClassRef> getMuzzleReferences() {
    return java.util.Collections.emptyMap();
  }

  @Override
  public void registerMuzzleVirtualFields(VirtualFieldMappingsBuilder builder) {}

  @Override
  public List<String> getMuzzleHelperClassNames() {
    return emptyList();
  }

  @Override
  public List<String> getAdditionalHelperClassNames() {
    return List.of(
        "io.scalac.mesmer.otelextension.instrumentations.akka.cluster.extension.AkkaClusterMonitorExtension",
        "io.scalac.mesmer.otelextension.instrumentations.akka.cluster.extension.AkkaClusterMonitorExtension$",
        "io.scalac.mesmer.otelextension.instrumentations.akka.cluster.extension.AkkaClusterMonitorExtension$$anon$1",
        "io.scalac.mesmer.otelextension.instrumentations.akka.cluster.extension.AkkaClusterMonitorExtensionId$",
        "io.scalac.mesmer.otelextension.instrumentations.akka.cluster.extension.ClusterEventsMonitor$",
        "io.scalac.mesmer.otelextension.instrumentations.akka.cluster.extension.ClusterEventsMonitor$MemberEventWrapper",
        "io.scalac.mesmer.otelextension.instrumentations.akka.cluster.extension.OnClusterStartup$",
        "io.scalac.mesmer.otelextension.instrumentations.akka.cluster.extension.OnClusterStartup$Initialized",
        "io.scalac.mesmer.otelextension.instrumentations.akka.cluster.extension.OnClusterStartup$Timeout$",
        "io.scalac.mesmer.otelextension.instrumentations.akka.cluster.extension.ClusterMonitorActor",
        "io.scalac.mesmer.otelextension.instrumentations.akka.cluster.extension.ClusterSelfNodeEventsActor$",
        "io.scalac.mesmer.otelextension.instrumentations.akka.cluster.extension.ClusterSelfNodeEventsActor$Command",
        "io.scalac.mesmer.otelextension.instrumentations.akka.cluster.extension.ClusterSelfNodeEventsActor$Command$ClusterMemberEvent",
        "io.scalac.mesmer.otelextension.instrumentations.akka.cluster.extension.ClusterSelfNodeEventsActor$Command$NodeUnreachable",
        "io.scalac.mesmer.otelextension.instrumentations.akka.cluster.extension.ClusterSelfNodeEventsActor$Command$NodeReachable",
        "io.scalac.mesmer.otelextension.instrumentations.akka.common.SerializableMessage",
        "io.scalac.mesmer.otelextension.instrumentations.akka.cluster.extension.ClusterRegionsMonitorActor$",
        "io.scalac.mesmer.otelextension.instrumentations.akka.cluster.extension.ClusterRegionsMonitorActor$Command",
        "io.scalac.mesmer.otelextension.instrumentations.akka.cluster.extension.ClusterRegionsMonitorActor$Regions",
        "io.scalac.mesmer.otelextension.instrumentations.akka.cluster.extension.ClusterRegionsMonitorActor$$anonfun$$nestedInanonfun$apply$2$1");
  }
}
