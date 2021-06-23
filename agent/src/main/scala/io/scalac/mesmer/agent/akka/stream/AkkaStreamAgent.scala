package io.scalac.mesmer.agent.akka.stream

import akka.ActorGraphInterpreterAdvice
import akka.actor.Props
import akka.stream.GraphStageIslandAdvice
import akka.stream.impl.fusing.{ ActorGraphInterpreterProcessEventAdvice, ActorGraphInterpreterTryInitAdvice }
import io.scalac.mesmer.agent.Agent
import io.scalac.mesmer.agent.akka.stream.impl.{
  ConnectionOps,
  GraphInterpreterPushAdvice,
  PhasedFusingActorMaterializerAdvice
}
import io.scalac.mesmer.agent.util.i13n._
import io.scalac.mesmer.core.model.{ Module, SupportedModules, SupportedVersion }
import io.scalac.mesmer.core.module.AkkaStreamModule

object AkkaStreamAgent
    extends InstrumentModuleFactory(AkkaStreamModule)
    with AkkaStreamModule.StreamMetricsDef[Agent]
    with AkkaStreamModule.StreamOperatorMetricsDef[Agent] {

  def agent(
    config: AkkaStreamModule.All[Boolean]
  ): Agent = Agent.empty ++
    (if (config.runningStreamsTotal) runningStreamsTotal else Agent.empty) ++
    (if (config.streamActorsTotal) streamActorsTotal else Agent.empty) ++
    (if (config.streamProcessedMessages) streamProcessedMessages else Agent.empty) ++
    (if (config.processedMessages) processedMessages else Agent.empty) ++
    (if (config.operators) operators else Agent.empty) ++
    (if (config.demand) demand else Agent.empty)

  lazy val runningStreamsTotal: Agent = sharedImplementations

  lazy val streamActorsTotal: Agent = sharedImplementations

  lazy val streamProcessedMessages: Agent = sharedImplementations

  lazy val processedMessages: Agent = sharedImplementations

  lazy val operators: Agent = sharedImplementations

  lazy val demand: Agent = sharedImplementations

  protected val supportedModules: SupportedModules = SupportedModules(Module("akka-stream"), SupportedVersion.any)

  /**
   * actorOf methods is called when island decide to materialize itself
   */
  private val phasedFusingActorMaterializerAgentInstrumentation =
    instrument("akka.stream.impl.PhasedFusingActorMaterializer".fqcn)
      .visit(PhasedFusingActorMaterializerAdvice, method("actorOf").takesArguments[Props, String])

  private val connectionPushAgent = {

    /**
     * Add incrementing push counter on push processing
     */
    val processPush = instrument("akka.stream.impl.fusing.GraphInterpreter".fqcnWithTags("push"))
      .visit(GraphInterpreterPushAdvice, "processPush")

    /**
     * Adds push counter to [[ akka.stream.impl.fusing.GraphInterpreter.Connection ]]
     */
    val pushField = instrument("akka.stream.impl.fusing.GraphInterpreter$Connection".fqcnWithTags("push"))
      .defineField[Long](ConnectionOps.PushCounterVarName)

    Agent(processPush, pushField)
  }

  private val connectionPullAgent = {

    /**
     * Add incrementing pull counter on pull processing
     */
    val processPull = instrument("akka.stream.impl.fusing.GraphInterpreter".fqcnWithTags("pull"))
      .visit(GraphInterpreterPushAdvice, "processPush")

    /**
     * Adds pull counter to [[ akka.stream.impl.fusing.GraphInterpreter.Connection ]]
     */
    val pullField = instrument("akka.stream.impl.fusing.GraphInterpreter$Connection".fqcnWithTags("pull"))
      .defineField[Long](ConnectionOps.PullCounterVarName)

    Agent(processPull, pullField)
  }

  /**
   * Instrumentation for Actor that execute streams - adds another message for it to handle that
   * pushes all connection data to EventBus and propagation of short living streams
   */
  private val actorGraphInterpreterInstrumentation =
    instrument("akka.stream.impl.fusing.ActorGraphInterpreter".fqcn)
      .visit[ActorGraphInterpreterAdvice]("receive")
      .visit(ActorGraphInterpreterProcessEventAdvice, "processEvent")
      .visit(ActorGraphInterpreterTryInitAdvice, "tryInit")

  /**
   * Instrumentation that add additional tag to terminal Sink
   */
  private val graphStageIslandInstrumentation =
    instrument("akka.stream.impl.GraphStageIsland".fqcn)
      .visit[GraphStageIslandAdvice]("materializeAtomic")

  private val sharedImplementations =
    connectionPullAgent ++ connectionPushAgent ++ actorGraphInterpreterInstrumentation ++ graphStageIslandInstrumentation ++ phasedFusingActorMaterializerAgentInstrumentation

}
