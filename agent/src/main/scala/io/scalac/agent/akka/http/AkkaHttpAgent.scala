package io.scalac.agent.akka.http

import net.bytebuddy.description.`type`.TypeDescription
import net.bytebuddy.description.method.MethodDescription
import net.bytebuddy.implementation.MethodDelegation
import net.bytebuddy.matcher.ElementMatchers
import net.bytebuddy.matcher.ElementMatchers._
import org.slf4j.LoggerFactory

import io.scalac.agent.Agent
import io.scalac.agent.Agent.LoadingResult
import io.scalac.agent.AgentInstrumentation
import io.scalac.core.model.Module
import io.scalac.core.model.SupportedModules
import io.scalac.core.model.SupportedVersion
import io.scalac.core.model.Version

object AkkaHttpAgent {

  // @ToDo tests all supported versions
  private[http] val defaultVersion    = Version(10, 2, 0)
  private[http] val supportedVersions = Seq(defaultVersion, Version(10, 1, 8))
  private[http] val moduleName        = Module("akka-http")

  private[http] val logger = LoggerFactory.getLogger(AkkaHttpAgent.getClass)

  private val routeAgent = AgentInstrumentation(
    "akka.http.scaladsl.server.Route$",
    SupportedModules(moduleName, SupportedVersion(supportedVersions))
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
    SupportedModules(moduleName, SupportedVersion(supportedVersions))
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

  val agent: Agent = Agent(httpAgent)
}
