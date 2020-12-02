package io.scalac.agent.akka.http

import io.scalac.agent.Agent.LoadingResult
import io.scalac.agent.{Agent, AgentInstrumentation}
import io.scalac.agent.model._
import net.bytebuddy.description.`type`.TypeDescription
import net.bytebuddy.description.method.MethodDescription
import net.bytebuddy.implementation.MethodDelegation
import net.bytebuddy.matcher.ElementMatchers
import net.bytebuddy.matcher.ElementMatchers._
import org.slf4j.LoggerFactory

object AkkaHttpAgent {

  // @ToDo tests all supported versions
  private val defaultVersion    = "10.2.0"
  private val supportedVersions = Seq(defaultVersion, "10.1.8").flatMap(Version.apply)
  private val moduleName        = Module("akka-http")

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
      .transform { (builder, typeDescription, classLoader, _) =>
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
