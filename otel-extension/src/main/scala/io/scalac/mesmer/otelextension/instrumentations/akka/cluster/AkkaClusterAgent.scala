package io.scalac.mesmer.otelextension.instrumentations.akka.cluster

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation

import io.scalac.mesmer.agent.util.dsl.matchers.isConstructor
import io.scalac.mesmer.agent.util.dsl.matchers.named
import io.scalac.mesmer.agent.util.i13n.Advice
import io.scalac.mesmer.agent.util.i13n.Instrumentation

object AkkaClusterAgent {

  val clusterMetricsExtension: TypeInstrumentation =
    Instrumentation(named("akka.actor.ActorSystemImpl"))
      .`with`(Advice(isConstructor, "akka.cluster.impl.ClusterMetricsExtensionAdvice"))
}
