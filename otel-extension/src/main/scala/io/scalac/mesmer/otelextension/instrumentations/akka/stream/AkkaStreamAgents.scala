package io.scalac.mesmer.otelextension.instrumentations.akka.stream

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation

import scala.jdk.CollectionConverters._

object AkkaStreamAgents {

  def getAllStreamInstrumentations: java.util.List[TypeInstrumentation] = {
    val old: List[TypeInstrumentation] = AkkaStreamAgent.agent.asOtelTypeInstrumentations.asScala.toList
    val migrated                       = List(AkkaStreamAgentMigrated.streamMetricsExtension)
    (old ++ migrated).asJava
  }
}
