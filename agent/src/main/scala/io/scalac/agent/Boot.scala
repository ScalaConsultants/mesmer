package io.scalac.agent

import java.lang.instrument.Instrumentation

import io.scalac.agent.akka.http.{RouteAgent, RouteInstrumentation}
import net.bytebuddy.ByteBuddy
import net.bytebuddy.agent.builder.AgentBuilder
import net.bytebuddy.description.`type`.TypeDescription
import net.bytebuddy.description.method.MethodDescription
import net.bytebuddy.dynamic.scaffold.TypeValidation
import net.bytebuddy.implementation.MethodDelegation
import net.bytebuddy.matcher.ElementMatchers
import net.bytebuddy.matcher.ElementMatchers._

object Boot {

  def premain(args: String, instrumentation: Instrumentation): Unit = {

    object AllInstrumentations extends AgentRoot with AkkaPersistenceAgent with RouteAgent {
      override lazy val agentBuilder: AgentBuilder =
        new AgentBuilder.Default()
          .`with`(new ByteBuddy().`with`(TypeValidation.DISABLED))
          .`with`(new AgentBuilder.InitializationStrategy.SelfInjection.Eager())
          .`with`(
            AgentBuilder.Listener.StreamWriting.toSystemOut.withTransformationsOnly
          )
          .`with`(AgentBuilder.InstallationListener.StreamWriting.toSystemOut)
    }

    AllInstrumentations.installOn(instrumentation)
    AllInstrumentations.transformEagerly()
//    AkkaPersistenceAgent.install(instrumentation)
//    AkkaPersistenceAgent.transformEagerly()
//
//    val configuredAgentBuilder = new AgentBuilder.Default()
//      .`with`(
//        AgentBuilder.Listener.StreamWriting.toSystemOut
//          .withTransformationsOnly()
//      )
//      .`with`(AgentBuilder.InstallationListener.StreamWriting.toSystemOut)
//
//    configuredAgentBuilder
//      .`type`(
//        ElementMatchers.nameEndsWithIgnoreCase[TypeDescription](
//          "akka.http.scaladsl.server.Route$"
//        )
//      )
//      .transform { (builder, typeDescription, classLoader, module) =>
//        builder
//          .method(
//            (named[MethodDescription]("asyncHandler")
//              .and(isMethod[MethodDescription])
//              .and(not(isAbstract[MethodDescription])))
//          )
//          .intercept(MethodDelegation.to(classOf[RouteInstrumentation]))
//      }
//      .installOn(instrumentation)
  }
}
