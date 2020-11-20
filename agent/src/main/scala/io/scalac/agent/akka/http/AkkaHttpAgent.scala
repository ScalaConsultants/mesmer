package io.scalac.agent.akka.http

import java.lang.instrument.Instrumentation
import java.net.URLClassLoader

import io.scalac.agent.Agent
import net.bytebuddy.agent.builder.AgentBuilder
import net.bytebuddy.description.`type`.TypeDescription
import net.bytebuddy.description.method.MethodDescription
import net.bytebuddy.implementation.MethodDelegation
import net.bytebuddy.matcher.ElementMatchers
import net.bytebuddy.matcher.ElementMatchers._

object AkkaHttpAgent {
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

  private val routeAgent = Agent(
    (agentBuilder: AgentBuilder, instrumentation: Instrumentation) =>
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
        .installOn(instrumentation),
    () => ()
  )

  private val httpAgent = Agent(
    (agentBuilder: AgentBuilder, instrumentation: Instrumentation) => {
      @volatile
      var shouldCheckLibraryVersion = true
      agentBuilder
        .`type`(
          ElementMatchers.nameEndsWithIgnoreCase[TypeDescription](
            "akka.http.scaladsl.HttpExt"
          )
        )
        .transform {
          (builder, typeDescription, classLoader, _) =>
            if (shouldCheckLibraryVersion) {
              detectAkkaHttpVersion(classLoader).fold {
                println("Could't detect akka-http version")
              }(version => println(s"Found akka http version: ${version}"))
              shouldCheckLibraryVersion = false
            }
            typeDescription.getTypeManifestation

            builder
              .method(
                (named[MethodDescription]("bindAndHandle")
                  .and(isMethod[MethodDescription])
                  .and(not(isAbstract[MethodDescription])))
              )
              .intercept(MethodDelegation.to(classOf[HttpInstrumentation]))
        }
        .installOn(instrumentation)
    },
    () => ()
  )

  val agent = httpAgent
}
