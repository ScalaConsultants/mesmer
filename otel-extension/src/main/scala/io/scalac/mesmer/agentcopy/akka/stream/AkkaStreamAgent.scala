package io.scalac.mesmer.agentcopy.akka.stream

import akka.{
  ActorGraphInterpreterOelAdvice,
  ActorGraphInterpreterProcessEventOtelAdvice,
  ActorGraphInterpreterTryInitOtelAdvice
}
import akka.stream.{ GraphInterpreterOtelPullAdvice, GraphInterpreterOtelPushAdvice, GraphStageIslandOtelAdvice }
import io.scalac.mesmer.agent.{ Agent, AgentInstrumentation }
import io.scalac.mesmer.agent.util.i13n._
import io.scalac.mesmer.agentcopy.akka.stream.impl.PhasedFusingActorMaterializerAdvice
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

    println(s"-=-=-=-=-=-=-=- Enabled config ${config} -=-=-=-=-=-=-=-=-")
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
   * Instrumentation for Actor that execute streams - adds another message for it to handle that pushes all connection
   * data to EventBus and propagation of short living streams
   */
  private val actorGraphInterpreterInstrumentation =
    AgentInstrumentation.deferred(
      instrument("akka.stream.impl.fusing.ActorGraphInterpreter".fqcn)
        .visit[ActorGraphInterpreterOelAdvice]("receive")
        .visit(ActorGraphInterpreterProcessEventOtelAdvice, "processEvent")
        .visit(ActorGraphInterpreterTryInitOtelAdvice, "tryInit")
    )

  /**
   * Instrumentation that add additional tag to terminal Sink
   */
  private val graphStageIslandInstrumentation =
    AgentInstrumentation.deferred(
      instrument("akka.stream.impl.GraphStageIsland".fqcn)
        .visit[GraphStageIslandOtelAdvice]("materializeAtomic")
    )

  private val sharedImplementations = connectionPushAgent ++ connectionPullAgent ++ actorGraphInterpreterInstrumentation ++ graphStageIslandInstrumentation ++ phasedFusingActorMaterializerAgentInstrumentation
//    connectionPullAgent ++ connectionPushAgent ++ actorGraphInterpreterInstrumentation ++ graphStageIslandInstrumentation ++ phasedFusingActorMaterializerAgentInstrumentation

}
