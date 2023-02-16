package io.scalac.mesmer.otelextension.instrumentations.akka.stream

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation

import io.scalac.mesmer.agent.util.dsl.matchers.isConstructor
import io.scalac.mesmer.agent.util.dsl.matchers.named
import io.scalac.mesmer.agent.util.i13n.Advice
import io.scalac.mesmer.agent.util.i13n.Instrumentation
import io.scalac.mesmer.agent.util.i13n.method

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

  /**
   * Instrumentation for Actors that execute streams: adds a message to be handled, that pushes all connection data to
   * EventBus including data regarding propagation of short living streams.
   */
  val actorGraphInterpreterInstrumentation: TypeInstrumentation =
    Instrumentation(named("akka.stream.impl.fusing.ActorGraphInterpreter"))
      .`with`(Advice(method("receive"), "akka.stream.impl.ActorGraphInterpreterAdvice"))
      .`with`(Advice(method("processEvent"), "akka.stream.impl.ActorGraphInterpreterProcessEventAdvice"))
      .`with`(Advice(method("tryInit"), "akka.stream.impl.ActorGraphInterpreterTryInitAdvice"))

}
