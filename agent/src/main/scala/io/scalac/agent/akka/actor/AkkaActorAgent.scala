package io.scalac.agent.akka.actor

import net.bytebuddy.asm.Advice
import net.bytebuddy.description.`type`.TypeDescription
import net.bytebuddy.description.method.MethodDescription
import net.bytebuddy.matcher.ElementMatchers._

import io.scalac.agent.Agent.LoadingResult
import io.scalac.agent.{ Agent, AgentInstrumentation }
import io.scalac.core.model.{ Module, SupportedModules, SupportedVersion, Version }
import io.scalac.core.support.ModulesSupport

object AkkaActorAgent {

  val moduleName: Module        = ModulesSupport.akkaActorModule
  val defaultVersion: Version   = Version(2, 6, 8)
  val version: SupportedVersion = ModulesSupport.akkaActor

  private val classicStashInstrumentation = {
    val targetClassName = "akka.actor.StashSupport"
    AgentInstrumentation(
      targetClassName,
      SupportedModules(moduleName, version)
    ) { (agentBuilder, instrumentation, _) =>
      agentBuilder
        .`type`(
          named[TypeDescription](targetClassName)
        )
        .transform { (builder, _, _, _) =>
          val advice = Advice.to(classOf[ClassicStashInstrumentation])
          builder
            .method(named[MethodDescription]("stash"))
            .intercept(advice)
            .method(named[MethodDescription]("prepend"))
            .intercept(advice)
            .method(named[MethodDescription]("unstash"))
            .intercept(advice)
            .method(named[MethodDescription]("unstashAll").and(takesArguments[MethodDescription](1)))
            .intercept(advice)
            .method(named[MethodDescription]("clearStash"))
            .intercept(advice)
        }
        .installOn(instrumentation)
      LoadingResult(targetClassName)
    }
  }

  val agent = Agent(classicStashInstrumentation)

}
