package io.scalac.mesmer.agent.akka.stream

import akka.ActorGraphInterpreterAdvice
import akka.stream.GraphStageIslandAdvice
import akka.stream.impl.fusing.ActorGraphInterpreterProcessEventAdvice
import akka.stream.impl.fusing.ActorGraphInterpreterTryInitAdvice

import io.scalac.mesmer.agent.Agent
import io.scalac.mesmer.agent.AgentInstrumentation
import io.scalac.mesmer.agent.akka.stream.impl.ConnectionOps
import io.scalac.mesmer.agent.akka.stream.impl.GraphInterpreterPullAdvice
import io.scalac.mesmer.agent.akka.stream.impl.GraphInterpreterPushAdvice
import io.scalac.mesmer.agent.akka.stream.impl.PhasedFusingActorMaterializerAdvice
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
    import module.enabled

    val runningStreamsTotalAgent     = if (enabled.runningStreamsTotal) runningStreamsTotal else Agent.empty
    val streamActorsTotalAgent       = if (enabled.runningStreamsTotal) streamActorsTotal else Agent.empty
    val streamProcessedMessagesAgent = if (enabled.runningStreamsTotal) streamProcessedMessages else Agent.empty
    val processedMessagesAgent       = if (enabled.runningStreamsTotal) processedMessages else Agent.empty
    val operatorsAgent               = if (enabled.runningStreamsTotal) operators else Agent.empty
    val demandAgent                  = if (enabled.runningStreamsTotal) demand else Agent.empty

    val resultantAgent =
      runningStreamsTotalAgent ++
        streamActorsTotalAgent ++
        streamProcessedMessagesAgent ++
        processedMessagesAgent ++
        operatorsAgent ++
        demandAgent

    resultantAgent
  }

  lazy val runningStreamsTotal: Agent = sharedImplementations

  lazy val streamActorsTotal: Agent = sharedImplementations

  lazy val streamProcessedMessages: Agent = sharedImplementations

  lazy val processedMessages: Agent = sharedImplementations

  lazy val operators: Agent = sharedImplementations

  lazy val demand: Agent = sharedImplementations

  /**
   * actorOf methods is called when island decide to materialize itself
   */
  private val phasedFusingActorMaterializerAgentInstrumentation =
    AgentInstrumentation.deferred(
      instrument(hierarchy("akka.stream.impl.ExtendedActorMaterializer".fqcn))
        .visit(PhasedFusingActorMaterializerAdvice, method("actorOf"))
    )

  private val connectionPushAgent = {

    /**
     * Add incrementing push counter on push processing
     */
    val processPush = AgentInstrumentation.deferred(
      instrument("akka.stream.impl.fusing.GraphInterpreter".fqcnWithTags("push"))
        .visit(GraphInterpreterPushAdvice, "processPush")
    )

    /**
     * Adds push counter to [[akka.stream.impl.fusing.GraphInterpreter.Connection]]
     */
    val pushField = AgentInstrumentation.deferred(
      instrument("akka.stream.impl.fusing.GraphInterpreter$Connection".fqcnWithTags("push"))
        .defineField[Long](ConnectionOps.PushCounterVarName)
    )

    Agent(processPush, pushField)
  }

  private val connectionPullAgent = {

    /**
     * Add incrementing pull counter on pull processing
     */
    val processPull = AgentInstrumentation.deferred(
      instrument("akka.stream.impl.fusing.GraphInterpreter".fqcnWithTags("pull"))
        .visit(GraphInterpreterPullAdvice, "processPull")
    )

    /**
     * Adds pull counter to [[akka.stream.impl.fusing.GraphInterpreter.Connection]]
     */
    val pullField = AgentInstrumentation.deferred(
      instrument("akka.stream.impl.fusing.GraphInterpreter$Connection".fqcnWithTags("pull"))
        .defineField[Long](ConnectionOps.PullCounterVarName)
    )

    Agent(processPull, pullField)
  }

  /**
   * Instrumentation for Actor that execute streams - adds another message for it to handle that pushes all connection
   * data to EventBus and propagation of short living streams
   */
  private val actorGraphInterpreterInstrumentation =
    AgentInstrumentation.deferred(
      instrument("akka.stream.impl.fusing.ActorGraphInterpreter".fqcn)
        .visit[ActorGraphInterpreterAdvice]("receive")
        .visit(ActorGraphInterpreterProcessEventAdvice, "processEvent")
        .visit(ActorGraphInterpreterTryInitAdvice, "tryInit")
    )

  /**
   * Instrumentation that add additional tag to terminal Sink
   */
  private val graphStageIslandInstrumentation =
    AgentInstrumentation.deferred(
      instrument("akka.stream.impl.GraphStageIsland".fqcn)
        .visit[GraphStageIslandAdvice]("materializeAtomic")
    )

  private val sharedImplementations =
    connectionPullAgent ++ connectionPushAgent ++ actorGraphInterpreterInstrumentation ++ graphStageIslandInstrumentation ++ phasedFusingActorMaterializerAgentInstrumentation

}
