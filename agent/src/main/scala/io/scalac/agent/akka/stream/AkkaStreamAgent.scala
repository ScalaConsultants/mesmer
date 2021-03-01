package io.scalac.agent.akka.stream

import akka.actor.Props
import akka.stream.GraphStageIslandAdvice
import akka.{ActorGraphInterpreterAdvice}
import io.scalac.agent.Agent.LoadingResult
import io.scalac.agent.{Agent, AgentInstrumentation}
import io.scalac.core.model.{Module, SupportedModules, SupportedVersion}
import net.bytebuddy.asm.Advice
import net.bytebuddy.description.`type`.TypeDescription
import net.bytebuddy.description.method.MethodDescription
import net.bytebuddy.matcher.ElementMatchers._

object AkkaStreamAgent {

  private[stream] val moduleName = Module("akka-stream")

  /**
   * actorOf methods is called when island decide to materialize itself
   */
  private val phasedFusingActorMeterializerAgent = AgentInstrumentation(
    "akka.stream.impl.PhasedFusingActorMaterializer",
    SupportedModules(moduleName, SupportedVersion.any)
  ) { (agentBuilder, instrumentation, _) =>
    agentBuilder
      .`type`(named[TypeDescription]("akka.stream.impl.PhasedFusingActorMaterializer"))
      .transform { (builder, _, _, _) =>
        builder
          .method(
            named[MethodDescription]("actorOf")
              .and(takesArguments(classOf[Props], classOf[String]))
          )
          .intercept(Advice.to(classOf[PhasedFusingActorMeterializerAdvice]))
      }
      .installOn(instrumentation)
    LoadingResult("akka.stream.impl.PhasedFusingActorMaterializer")
  }

  private val graphInterpreterAgent = AgentInstrumentation(
    "akka.stream.impl.fusing.GraphInterpreter",
    SupportedModules(moduleName, SupportedVersion.any)
  ) { (agentBuilder, instrumentation, _) =>
    agentBuilder
      .`type`(named[TypeDescription]("akka.stream.impl.fusing.GraphInterpreter"))
      .transform { (builder, _, _, _) =>
        builder
          .method(
            named[MethodDescription]("processPush")
          )
          .intercept(Advice.to(classOf[GraphInterpreterPushAdvice]))
          .method(
            named[MethodDescription]("processPull")
          )
          .intercept(Advice.to(classOf[GraphInterpreterPullAdvice]))

      }
      .installOn(instrumentation)
    LoadingResult("akka.stream.impl.fusing.GraphInterpreter")
  }

  private val graphInterpreterConnectionAgent = {
    val target = "akka.stream.impl.fusing.GraphInterpreter$Connection"
    AgentInstrumentation(
      target,
      SupportedModules(moduleName, SupportedVersion.any)
    ) { (agentBuilder, instrumentation, _) =>
      agentBuilder
        .`type`(named[TypeDescription](target))
        .transform { (builder, _, _, _) =>
          builder
            .defineField(ConnectionOps.PushCounterVarName, classOf[Long]) // java specification guarantee starting from 0
            .defineField(ConnectionOps.PullCounterVarName, classOf[Long])
        }
        .installOn(instrumentation)
      LoadingResult(target)
    }
  }

  private val actorGraphInterpreterAgent = AgentInstrumentation(
    "akka.stream.impl.fusing.ActorGraphInterpreter",
    SupportedModules(moduleName, SupportedVersion.any)
  ) { (agentBuilder, instrumentation, _) =>
    agentBuilder
      .`type`(named[TypeDescription]("akka.stream.impl.fusing.ActorGraphInterpreter"))
      .transform { (builder, _, _, _) =>
        builder
          .method(
            named[MethodDescription]("receive")
          )
          .intercept(Advice.to(classOf[ActorGraphInterpreterAdvice]))
      }
      .installOn(instrumentation)
    LoadingResult("akka.stream.impl.fusing.ActorGraphInterpreter")
  }

  private val graphStageIslandAgent = {
    val target = "akka.stream.impl.GraphStageIsland"
    AgentInstrumentation(
      target,
      SupportedModules(moduleName, SupportedVersion.any)
    ) { (agentBuilder, instrumentation, _) =>
      agentBuilder
        .`type`(named[TypeDescription](target))
        .transform { (builder, _, _, _) =>
          builder
            .visit(
              Advice
                .to(classOf[GraphStageIslandAdvice])
                .on(named[MethodDescription]("materializeAtomic"))
            )
        }
        .installOn(instrumentation)
      LoadingResult(target)
    }
  }

  val agent = Agent(
    phasedFusingActorMeterializerAgent,
    actorGraphInterpreterAgent,
    graphInterpreterAgent,
    graphInterpreterConnectionAgent,
    graphStageIslandAgent
  )

}
