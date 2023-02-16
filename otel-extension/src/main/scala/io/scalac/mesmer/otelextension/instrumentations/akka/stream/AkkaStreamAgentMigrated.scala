package io.scalac.mesmer.otelextension.instrumentations.akka.stream

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation

import io.scalac.mesmer.agent.util.dsl.matchers.isConstructor
import io.scalac.mesmer.agent.util.dsl.matchers.named
import io.scalac.mesmer.agent.util.i13n.Advice
import io.scalac.mesmer.agent.util.i13n.Instrumentation

/**
 * The difference from AkkaStreamAgent is that here we migrate the streaming metrics to cooperate with the otelExtension
 * without using the AkkaMonitoring Akka extension and use the new DSL for creating instrumentations
 *
 * @see
 *   io.scalac.mesmer.otelextension.instrumentations.akka.stream.AkkaStreamAgent
 *   io.scalac.mesmer.agent.util.i13n.Instrumentation
 */
object AkkaStreamAgentMigrated {

  val streamMetricsExtension: TypeInstrumentation =
    Instrumentation(named("akka.actor.ActorSystemImpl"))
      .`with`(Advice(isConstructor, "akka.stream.impl.StreamMetricsExtensionAdvice"))

}
