package io.scalac.agent.akka.cluster

import io.scalac.agent.Agent.LoadingResult
import io.scalac.agent.{ Agent, AgentInstrumentation }
import io.scalac.core.model.SupportedModules
import io.scalac.core.support.ModulesSupport._
import net.bytebuddy.asm.Advice
import net.bytebuddy.description.`type`.TypeDescription
import net.bytebuddy.description.method.MethodDescription
import net.bytebuddy.matcher.ElementMatchers
import net.bytebuddy.matcher.ElementMatchers.{ isAbstract, isMethod, named, not }

object AkkaClusterAgent {

  private val shardingAgent = AgentInstrumentation(
    "akka.cluster.sharding.ClusterSharding",
    SupportedModules(akkaClusterTypedModule, akkaClusterTyped)
  ) { (builder, instrumentation, _) =>
    builder
      .`type`(
        ElementMatchers.nameEndsWithIgnoreCase[TypeDescription](
          "akka.cluster.sharding.ClusterSharding"
        )
      )
      .transform { (builder, _, _, _) =>
        builder
          .method(
            (named[MethodDescription]("internalStart")
              .and(isMethod[MethodDescription])
              .and(not(isAbstract[MethodDescription])))
          )
          .intercept(Advice.to(classOf[ClusterShardingInterceptor]))
      }
      .installOn(instrumentation)
    LoadingResult("akka.cluster.sharding.ClusterSharding")
  }

  val agent = Agent(shardingAgent)
}
