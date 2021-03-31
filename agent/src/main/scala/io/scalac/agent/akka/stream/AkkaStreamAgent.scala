package io.scalac.agent.akka.stream

import akka.ActorGraphInterpreterAdvice
import akka.actor.Props
import akka.stream.GraphStageIslandAdvice
import akka.stream.impl.fusing.{ ActorGraphInterpreterProcessEventAdvice, ActorGraphInterpreterTryInitAdvice }

import io.scalac.agent.Agent
import io.scalac.agent.util.i13n._
import io.scalac.core.model.{ Module, SupportedModules, SupportedVersion }

object AkkaStreamAgent extends InstrumentModuleFactory {

  private[akka] val moduleName = Module("akka-stream")

  protected val supportedModules: SupportedModules = SupportedModules(moduleName, SupportedVersion.any)

  /**
   * actorOf methods is called when island decide to materialize itself
   */
  private val phasedFusingActorMeterializerAgent = instrument("akka.stream.impl.PhasedFusingActorMaterializer")(
    _.intercept[PhasedFusingActorMeterializerAdvice](method("actorOf").takesArguments[Props, String])
  )

  private val graphInterpreterAgent = instrument("akka.stream.impl.fusing.GraphInterpreter")(
    _.intercept[GraphInterpreterPushAdvice]("processPush")
      .intercept[GraphInterpreterPullAdvice]("processPull")
  )

  private val graphInterpreterConnectionAgent = instrument("akka.stream.impl.fusing.GraphInterpreter$Connection")(
    _.defineField[Long](ConnectionOps.PullCounterVarName)
      .defineField[Long](ConnectionOps.PushCounterVarName)
  )

  private val actorGraphInterpreterAgent = instrument("akka.stream.impl.fusing.ActorGraphInterpreter")(
    _.visit[ActorGraphInterpreterAdvice]("receive")
      .visit[ActorGraphInterpreterProcessEventAdvice]("processEvent")
      .visit[ActorGraphInterpreterTryInitAdvice]("tryInit")
  )

  private val graphStageIslandAgent = instrument("akka.stream.impl.GraphStageIsland")(
    _.visit[GraphStageIslandAdvice]("materializeAtomic")
  )

  val agent = Agent(
    phasedFusingActorMeterializerAgent,
    actorGraphInterpreterAgent,
    graphInterpreterAgent,
    graphInterpreterConnectionAgent,
    graphStageIslandAgent
  )
}
