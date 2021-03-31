package io.scalac.agent.akka.http

import io.scalac.agent.Agent
import io.scalac.agent.util.i13n._
import io.scalac.core.model.SupportedModules
import io.scalac.core.support.ModulesSupport

object AkkaHttpAgent extends InstrumentModuleFactory {

  // @ToDo tests all supported versions
  protected final val supportedModules = SupportedModules(ModulesSupport.akkaHttpModule, ModulesSupport.akkaHttp)

//  private val routeAgent = instrument("akka.http.scaladsl.server.Route$")(
//    _.delegate[RouteInstrumentation]("asyncHandler")
//  )

  private val httpAgent = instrument("akka.http.scaladsl.HttpExt")(
    _.delegate[HttpInstrumentation]("bindAndHandle")
  )

  val agent: Agent = Agent(httpAgent)

}
