package io.scalac.agent.akka.cluster

import io.scalac.agent.Agent.LoadingResult
import io.scalac.agent.{Agent, AgentInstrumentation}
import io.scalac.core.model.{Module, SupportedModules, SupportedVersion, Version}
import net.bytebuddy.asm.Advice
import net.bytebuddy.description.`type`.TypeDescription
import net.bytebuddy.description.method.MethodDescription
import net.bytebuddy.matcher.ElementMatchers
import net.bytebuddy.matcher.ElementMatchers.{isAbstract, isMethod, named, not}

object AkkaClusterAgent {

  private val supportedModules = SupportedModules(Module("akka-cluster"), SupportedVersion(Version(2, 6, 8)))

  private val shardingAgent = AgentInstrumentation("akka.cluster.sharding.ClusterSharding", supportedModules) {
    (builder, instrumentation, _) =>
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
