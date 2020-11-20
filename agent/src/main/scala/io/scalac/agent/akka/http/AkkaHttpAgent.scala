package io.scalac.agent.akka.http

import java.lang.instrument.Instrumentation
import java.net.URLClassLoader

import io.scalac.agent.Agent
import io.scalac.agent.util.ModuleInfo.Modules
import net.bytebuddy.agent.builder.AgentBuilder
import net.bytebuddy.description.`type`.TypeDescription
import net.bytebuddy.description.method.MethodDescription
import net.bytebuddy.implementation.MethodDelegation
import net.bytebuddy.matcher.ElementMatchers
import net.bytebuddy.matcher.ElementMatchers._

object AkkaHttpAgent {

  // @ToDo tests all supported versions
  private val defaultVersion    = "10.2.0"
  private val supportedVersions = Seq(defaultVersion, "10.1.8")
  private val moduleName        = "akka-http"

  val akkaHttpRegex = "^akka-http_(.+?)-(.+?)\\.jar$".r

  private def detectAkkaHttpVersion(loader: ClassLoader): Option[String] =
    loader match {
      case urLClassLoader: URLClassLoader =>
        urLClassLoader.getURLs
          .find(_.getFile.startsWith("akka-http"))
          .flatMap { url =>
            url.getFile match {
              case akkaHttpRegex(_, akkaHttpVersion) => Some(akkaHttpVersion)
              case _                                 => None
            }
          }
      case _ =>
        None
    }

  private val routeAgent = Agent { (agentBuilder, instrumentation, _) =>
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
    List("akka.http.scaladsl.server.Route$")
  }

  private val httpAgent = Agent { (agentBuilder: AgentBuilder, instrumentation: Instrumentation, modules: Modules) =>
    modules
      .get(moduleName)
      .orElse {
        println(s"No version found for module ${moduleName}, selecting default")
        Some(defaultVersion)
      }
      .filter(supportedVersions.contains)
      .fold[List[String]] {
        println(s"Cannot instrument ${moduleName} - unsupported version found")
        Nil
      } { version => //here comes branching
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
          }.installOn(instrumentation)
        List("akka.http.scaladsl.HttpExt")
      }
  }

  val agent = httpAgent
}
