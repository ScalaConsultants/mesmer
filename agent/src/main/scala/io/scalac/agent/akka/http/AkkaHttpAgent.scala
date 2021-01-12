package io.scalac.agent.akka.http

import io.scalac.agent.Agent.LoadingResult
import io.scalac.agent.{ Agent, AgentInstrumentation }
import io.scalac.core.model.SupportedModules
import io.scalac.core.support.ModulesSupport._
import net.bytebuddy.description.`type`.TypeDescription
import net.bytebuddy.description.method.MethodDescription
import net.bytebuddy.implementation.MethodDelegation
import net.bytebuddy.matcher.ElementMatchers
import net.bytebuddy.matcher.ElementMatchers._
import org.slf4j.LoggerFactory

object AkkaHttpAgent {

  private[http] val logger = LoggerFactory.getLogger(AkkaHttpAgent.getClass)

  private val supportedModules: SupportedModules = SupportedModules(akkaHttpModule, akkaHttp)

  private val routeAgent = AgentInstrumentation(
    "akka.http.scaladsl.server.Route$",
    supportedModules
  ) { (agentBuilder, instrumentation, _) =>
    agentBuilder
      .`type`(
        ElementMatchers.nameEndsWithIgnoreCase[TypeDescription](
          "akka.http.scaladsl.server.Route$"
        )
      )
      .transform { (builder, typeDescription, classLoader, module) =>
        builder
          .method(
            (named[MethodDescription]("asyncHandler")
              .and(isMethod[MethodDescription])
              .and(not(isAbstract[MethodDescription])))
          )
          .intercept(MethodDelegation.to(classOf[RouteInstrumentation]))
      }
      .installOn(instrumentation)
    LoadingResult("akka.http.scaladsl.server.Route$")
  }

  private val httpAgent = AgentInstrumentation(
    "akka.http.scaladsl.HttpExt",
    supportedModules
  ) { (agentBuilder, instrumentation, _) =>
    agentBuilder
      .`type`(
        ElementMatchers.nameEndsWithIgnoreCase[TypeDescription](
          "akka.http.scaladsl.HttpExt"
        )
      )
      .transform { (builder, _, _, _) =>
        builder
          .method(
            (named[MethodDescription]("bindAndHandle")
              .and(isMethod[MethodDescription])
              .and(not(isAbstract[MethodDescription])))
          )
          .intercept(MethodDelegation.to(classOf[HttpInstrumentation]))
      }
      .installOn(instrumentation)
    LoadingResult("akka.http.scaladsl.HttpExt")
  }

  val agent = Agent(httpAgent)
}
