package io.scalac.agent.akka.http

import java.lang.instrument.Instrumentation

import io.scalac.agent.Agent
import net.bytebuddy.description.`type`.TypeDescription
import net.bytebuddy.description.method.MethodDescription
import net.bytebuddy.implementation.MethodDelegation
import net.bytebuddy.matcher.ElementMatchers
import net.bytebuddy.matcher.ElementMatchers._

trait RouteAgent extends Agent {
  abstract override def installOn(instrumentation: Instrumentation): Unit = {
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
    super.installOn(instrumentation)
  }
}
