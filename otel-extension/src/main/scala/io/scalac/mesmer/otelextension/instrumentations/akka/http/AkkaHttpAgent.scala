package io.scalac.mesmer.otelextension.instrumentations.akka.http

import io.scalac.mesmer.agent.Agent
import io.scalac.mesmer.agent.util.i13n.{ InstrumentModuleFactory, _ }
import io.scalac.mesmer.core.module.{ MesmerModule, MetricsModule, Module }
import io.scalac.mesmer.core.typeclasses.{ Combine, Traverse }
import io.scalac.mesmer.instrumentation.http.HttpExtConnectionAdvice

object AkkaHttpAgent extends InstrumentModuleFactory(AkkaHttpModule) with AkkaHttpModule.All[Agent] {

  override val connections: Agent =
    Agent(instrument("akka.http.scaladsl.HttpExt".fqcn).visit[HttpExtConnectionAdvice]("bindAndHandle"))

  override def agent: Agent = {
    val config: AkkaHttpModule.Config = module.enabled
    connections.onCondition(config.connections)
  }
}

object AkkaHttpModule extends MesmerModule with AkkaHttpMetricsModule {
  override val name: String = "akkahttp"

  override type Metrics[T] = AkkaHttpMetricsDef[T]

  override type All[T] = Metrics[T]

  case class HttpModuleConfig(connections: Boolean) extends AkkaHttpMetricsDef[Boolean]

  val defaultConfig: AkkaHttpModule.Result = HttpModuleConfig(true)

  override protected def fromMap(properties: Map[String, Boolean]): HttpModuleConfig =
    HttpModuleConfig(connections = properties.getOrElse("connections", defaultConfig.connections))

  implicit val combineConfig: Combine[All[Boolean]] = (first, second) =>
    HttpModuleConfig(first.connections && second.connections)

  implicit val traverseAll: Traverse[All] = new Traverse[All] {
    def sequence[T](obj: All[T]): Seq[T] = Seq(obj.connections)
  }
}

sealed trait AkkaHttpMetricsModule extends MetricsModule {
  this: Module =>

  override type Metrics[T] <: AkkaHttpMetricsDef[T]

  trait AkkaHttpMetricsDef[T] {
    def connections: T
  }
}
