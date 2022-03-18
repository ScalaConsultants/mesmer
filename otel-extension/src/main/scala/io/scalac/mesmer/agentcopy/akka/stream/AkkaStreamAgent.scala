package io.scalac.mesmer.agentcopy.akka.stream

import akka.ActorGraphInterpreterOelAdvice
import akka.stream.GraphStageIslandOtelAdvice
import akka.stream.impl.fusing.{ActorGraphInterpreterProcessEventAdvice, ActorGraphInterpreterTryInitAdvice}
import io.scalac.mesmer.agent.{Agent, AgentInstrumentation}
import io.scalac.mesmer.agentcopy.akka.stream.impl.{ConnectionOps, GraphInterpreterPullAdvice, GraphInterpreterPushAdvice, PhasedFusingActorMaterializerAdvice}
import io.scalac.mesmer.agent.util.i13n._
import io.scalac.mesmer.core.akka.version26x
import io.scalac.mesmer.core.model.Version
import io.scalac.mesmer.core.module.AkkaStreamModule
import scala.Tuple2

object AkkaStreamAgent
    extends InstrumentModuleFactory(AkkaStreamModule)
    with AkkaStreamModule.StreamMetricsDef[AkkaStreamModule.Jars[Version] => Option[Agent]]
    with AkkaStreamModule.StreamOperatorMetricsDef[AkkaStreamModule.Jars[Version] => Option[Agent]] {

  /**
   * @param config
   *   configuration of features that are wanted by the user
   * @param jars
   *   versions of required jars to deduce which features can be enabled
   * @return
   *   Resulting agent and resulting configuration based on runtime properties
   */
  protected def agent(
    config: AkkaStreamModule.All[Boolean],
    jars: AkkaStreamModule.AkkaStreamsJars[Version]
  ): (Agent, AkkaStreamModule.All[Boolean]) = {
    val runningStreamsTotalAgent     = if (config.runningStreamsTotal) runningStreamsTotal(jars) else None
    val streamActorsTotalAgent       = if (config.runningStreamsTotal) streamActorsTotal(jars) else None
    val streamProcessedMessagesAgent = if (config.runningStreamsTotal) streamProcessedMessages(jars) else None
    val processedMessagesAgent       = if (config.runningStreamsTotal) processedMessages(jars) else None
    val operatorsAgent               = if (config.runningStreamsTotal) operators(jars) else None
    val demandAgent                  = if (config.runningStreamsTotal) demand(jars) else None

    val resultantAgent =
      runningStreamsTotalAgent.getOrElse(Agent.empty) ++
        streamActorsTotalAgent.getOrElse(Agent.empty) ++
        streamProcessedMessagesAgent.getOrElse(Agent.empty) ++
        processedMessagesAgent.getOrElse(Agent.empty) ++
        operatorsAgent.getOrElse(Agent.empty) ++
        demandAgent.getOrElse(Agent.empty)

    val enabled = AkkaStreamModule.Impl(
      runningStreamsTotal = runningStreamsTotalAgent.isDefined,
      streamActorsTotal = streamActorsTotalAgent.isDefined,
      streamProcessedMessages = streamProcessedMessagesAgent.isDefined,
      processedMessages = processedMessagesAgent.isDefined,
      operators = operatorsAgent.isDefined,
      demand = demandAgent.isDefined
    )
    (resultantAgent, enabled)
  }

  private def ifSupported(agent: => Agent)(versions: AkkaStreamModule.Jars[Version]): Option[Agent] = {
    import versions._
    if (version26x.supports(akkaStream) && version26x.supports(akkaActorTyped) && version26x.supports(akkaActor)) {
      Some(agent)
    } else None
  }

  lazy val runningStreamsTotal: AkkaStreamModule.Jars[Version] => Option[Agent] = ifSupported(sharedImplementations)

  lazy val streamActorsTotal: AkkaStreamModule.Jars[Version] => Option[Agent] = ifSupported(sharedImplementations)

  lazy val streamProcessedMessages: AkkaStreamModule.Jars[Version] => Option[Agent] = ifSupported(
    sharedImplementations
  )

  lazy val processedMessages: AkkaStreamModule.Jars[Version] => Option[Agent] = ifSupported(sharedImplementations)

  lazy val operators: AkkaStreamModule.Jars[Version] => Option[Agent] = ifSupported(sharedImplementations)

  lazy val demand: AkkaStreamModule.Jars[Version] => Option[Agent] = ifSupported(sharedImplementations)

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
//        .defineField[Long](ConnectionOps.PushCounterVarName)
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
//        .defineField[Long](ConnectionOps.PullCounterVarName)
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
        .visit[ActorGraphInterpreterOelAdvice]("receive")
        .visit(ActorGraphInterpreterProcessEventAdvice, "processEvent")
        .visit(ActorGraphInterpreterTryInitAdvice, "tryInit")
    )

  /**
   * Instrumentation that add additional tag to terminal Sink
   */
  private val graphStageIslandInstrumentation =
    AgentInstrumentation.deferred(
      instrument("akka.stream.impl.GraphStageIsland".fqcn)
        .visit[GraphStageIslandOtelAdvice]("materializeAtomic")
    )

  private val sharedImplementations =
    connectionPullAgent ++ connectionPushAgent ++ actorGraphInterpreterInstrumentation ++ graphStageIslandInstrumentation ++ phasedFusingActorMaterializerAgentInstrumentation

}
