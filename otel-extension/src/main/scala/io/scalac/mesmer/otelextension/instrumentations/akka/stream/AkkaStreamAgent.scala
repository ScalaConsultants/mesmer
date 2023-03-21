package io.scalac.mesmer.otelextension.instrumentations.akka.stream

import akka.stream.ConnectionConstructorAdvice
import akka.stream.GraphInterpreterOtelPullAdvice
import akka.stream.GraphInterpreterOtelPushAdvice
import akka.stream.GraphStageIslandOtelAdvice

import io.scalac.mesmer.agent.Agent
import io.scalac.mesmer.agent.AgentInstrumentation
import io.scalac.mesmer.agent.util.i13n._
import io.scalac.mesmer.core.module.AkkaStreamModule

object AkkaStreamAgent
    extends InstrumentModuleFactory(AkkaStreamModule)
    with AkkaStreamModule.StreamMetricsDef[Agent]
    with AkkaStreamModule.StreamOperatorMetricsDef[Agent] {

  /**
   * @param config
   *   configuration of features that are wanted by the user
   * @param jars
   *   versions of required jars to deduce which features can be enabled
   * @return
   *   Resulting agent and resulting configuration based on runtime properties
   */
  def agent: Agent = {
    val config = module.enabled

    List(
      runningStreamsTotal.onCondition(config.runningStreamsTotal),
      streamActorsTotal.onCondition(config.streamActorsTotal),
      streamProcessedMessages.onCondition(config.streamProcessedMessages),
      processedMessages.onCondition(config.processedMessages),
      operators.onCondition(config.operators),
      demand.onCondition(config.demand)
    ).reduce(_ ++ _)
  }

  lazy val runningStreamsTotal: Agent = sharedImplementations

  lazy val streamActorsTotal: Agent = sharedImplementations

  lazy val streamProcessedMessages: Agent = sharedImplementations

  lazy val processedMessages: Agent = sharedImplementations

  lazy val operators: Agent = sharedImplementations

  lazy val demand: Agent = sharedImplementations

  private val connectionConstructorAdvice = {

    /**
     * Add incrementing push counter on push processing
     */
    val processPush = AgentInstrumentation.deferred(
      instrument("akka.stream.impl.fusing.GraphInterpreter$Connection".fqcn)
        .visit[ConnectionConstructorAdvice](constructor)
    )

    Agent(processPush)
  }

  private val connectionPushAgent = {

    /**
     * Add incrementing push counter on push processing
     */
    val processPush = AgentInstrumentation.deferred(
      instrument("akka.stream.impl.fusing.GraphInterpreter".fqcnWithTags("push"))
        .visit(GraphInterpreterOtelPushAdvice, "processPush")
    )

    Agent(processPush)
  }

  private val connectionPullAgent = {

    /**
     * Add incrementing pull counter on pull processing
     */
    val processPull = AgentInstrumentation.deferred(
      instrument("akka.stream.impl.fusing.GraphInterpreter".fqcnWithTags("pull"))
        .visit(GraphInterpreterOtelPullAdvice, "processPull")
    )

    Agent(processPull)
  }

  /**
   * Instrumentation that add additional tag to terminal Sink
   */
  private val graphStageIslandInstrumentation =
    AgentInstrumentation.deferred(
      instrument("akka.stream.impl.GraphStageIsland".fqcn)
        .visit[GraphStageIslandOtelAdvice]("materializeAtomic")
    )

  private val sharedImplementations =
    connectionConstructorAdvice ++ connectionPushAgent ++ connectionPullAgent ++ graphStageIslandInstrumentation

}
