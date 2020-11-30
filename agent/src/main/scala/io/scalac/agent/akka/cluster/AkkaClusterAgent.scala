package io.scalac.agent.akka.cluster

import io.scalac.agent.Agent
import net.bytebuddy.asm.Advice
import net.bytebuddy.description.`type`.TypeDescription
import net.bytebuddy.description.method.MethodDescription
import net.bytebuddy.matcher.ElementMatchers
import net.bytebuddy.matcher.ElementMatchers.{ isAbstract, isMethod, named, not }

object AkkaClusterAgent {

  private val shardingAgent = Agent(
    {
      case (builder, instrumentation) =>
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
    },
    () => ()
  )

  val agent = shardingAgent
}
