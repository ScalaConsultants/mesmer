package io.scalac.agent.akka.stream

import akka.actor.Props
import akka.{ ActorGraphInterpreterAdvice, GraphInterpreterPushAdvice }
import io.scalac.agent.Agent.LoadingResult
import io.scalac.agent.{ Agent, AgentInstrumentation }
import io.scalac.core.model.{ Module, SupportedModules, SupportedVersion }
import net.bytebuddy.asm.Advice
import net.bytebuddy.description.`type`.TypeDescription
import net.bytebuddy.description.method.MethodDescription
import net.bytebuddy.matcher.ElementMatchers._
import net.bytebuddy.pool.TypePool

object AkkaStreamAgent {

  private[stream] val moduleName = Module("akka-stream")

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
    val types = TypePool.Default.ofSystemLoader()
    agentBuilder
      .`type`(named[TypeDescription]("akka.stream.impl.fusing.GraphInterpreter"))
      .transform { (builder, _, _, _) =>
        builder
          .defineField("pushCounter", classOf[Int])
          .defineField("pullCounter", classOf[Int])
          .method(
            named[MethodDescription]("processPush")
          )
          .intercept(Advice.to(classOf[GraphInterpreterPushAdvice]))

      }
      .installOn(instrumentation)
    LoadingResult("akka.stream.impl.fusing.GraphInterpreter")
  }

  private val actorGraphInterpreterAgent = AgentInstrumentation(
    "akka.stream.impl.fusing.ActorGraphInterpreter",
    SupportedModules(moduleName, SupportedVersion.any)
  ) { (agentBuilder, instrumentation, _) =>
    val types = TypePool.Default.ofSystemLoader()
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

  val agent = Agent(phasedFusingActorMeterializerAgent, actorGraphInterpreterAgent, graphInterpreterAgent)

}
