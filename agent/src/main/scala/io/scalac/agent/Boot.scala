package io.scalac.agent

import java.lang.instrument.Instrumentation

import io.scalac.agent.akka.http.RouteInstrumentation
import net.bytebuddy.agent.builder.AgentBuilder
import net.bytebuddy.description.`type`.TypeDescription
import net.bytebuddy.description.method.MethodDescription
import net.bytebuddy.implementation.MethodDelegation
import net.bytebuddy.matcher.ElementMatchers
import net.bytebuddy.matcher.ElementMatchers._

object Boot {

  def premain(args: String, instrumentation: Instrumentation): Unit = {
    AkkaPersistenceAgent.install(instrumentation)
    AkkaPersistenceAgent.transformEagerly()

    val configuredAgentBuilder = new AgentBuilder.Default()
      .`with`(
        AgentBuilder.Listener.StreamWriting.toSystemOut
          .withTransformationsOnly()
      )
      .`with`(AgentBuilder.InstallationListener.StreamWriting.toSystemOut)

    configuredAgentBuilder
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
  }
}
