package io.scalac.agent.akka.stream

import akka.actor.Props
import io.scalac.agent.Agent.LoadingResult
import io.scalac.agent.AgentInstrumentation
import io.scalac.core.model.{ Module, SupportedModules, SupportedVersion }
import net.bytebuddy.asm.Advice
import net.bytebuddy.description.`type`.TypeDescription
import net.bytebuddy.description.method.MethodDescription
import net.bytebuddy.matcher.ElementMatchers._

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

  val agent = phasedFusingActorMeterializerAgent

}
