package io.scalac.agent.akka.actor

import akka.actor.typed.Behavior

import net.bytebuddy.asm.Advice
import net.bytebuddy.description.`type`.TypeDescription
import net.bytebuddy.description.method.MethodDescription
import net.bytebuddy.matcher.ElementMatchers._

import io.scalac.agent.Agent.LoadingResult
import io.scalac.agent.{ Agent, AgentInstrumentation }
import io.scalac.core.model._
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
            .method(
              named[MethodDescription]("stash")
                .or(named[MethodDescription]("prepend"))
                .or(named[MethodDescription]("unstash"))
                .or(
                  named[MethodDescription]("unstashAll")
                    .and(takesArguments[MethodDescription](1))
                )
                .or(named[MethodDescription]("clearStash"))
            )
            .intercept(advice)
        }
        .installOn(instrumentation)
      LoadingResult(targetClassName)
    }
  }

  private val typedStashInstrumentation = {
    val targetClassName = "akka.actor.typed.internal.StashBufferImpl"
    AgentInstrumentation(
      targetClassName,
      SupportedModules(moduleName, version)
    ) { (agentBuilder, instrumentation, _) =>
      agentBuilder
        .`type`(
          named[TypeDescription](targetClassName)
        )
        .transform { (builder, _, _, _) =>
          val advice = Advice.to(classOf[TypeStashInstrumentation])
          builder
            .method(
              named[MethodDescription]("stash")
                .or(named[MethodDescription]("clear"))
                .or(
                  named[MethodDescription]("unstash")
                  // since there're two `unstash` methods, we need to specify paramter types
                    .and(takesArguments(classOf[Behavior[_]], classOf[Int], classOf[Function1[_, _]]))
                )
            )
            .intercept(advice)
        }
        .installOn(instrumentation)
      LoadingResult(targetClassName)
    }
  }

  val agent = Agent(classicStashInstrumentation, typedStashInstrumentation)

}
