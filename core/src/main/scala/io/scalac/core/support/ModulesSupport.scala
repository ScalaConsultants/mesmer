package io.scalac.core.support

import io.scalac.core.model.SupportedVersion._
import io.scalac.core.model.{ Module, SupportedVersion }

trait ModulesSupport {
  def akkaActor: SupportedVersion
  def akkaHttp: SupportedVersion
  def akkaStream: SupportedVersion
  def akkaPersistenceTyped: SupportedVersion
  def akkaClusterTyped: SupportedVersion
}

object ModulesSupport extends ModulesSupport {
  val akkaHttpModule: Module             = Module("akka-http")
  val akkaClusterTypedModule: Module     = Module("akka-cluster-typed")
  val akkaPersistenceTypedModule: Module = Module("akka-persistence-typed")
  val akkaActorTypedModule: Module       = Module("akka-actor-typed")
  val akkaActorModule: Module            = Module("akka-actor")
  val akkaStreamModule: Module           = Module("akka-stream")

  private val commonAkkaSupportedVersion: SupportedVersion =
    majors("2").and(minors("6")).and(patches("8", "9", "10", "11", "12"))

  override val akkaActor: SupportedVersion = commonAkkaSupportedVersion

  override val akkaHttp: SupportedVersion =
    majors("10")
      .and(minors("1").and(patches("8")).or(minors("2").and(patches("0", "1", "2", "3"))))

  override val akkaPersistenceTyped: SupportedVersion =
    commonAkkaSupportedVersion

  override val akkaClusterTyped: SupportedVersion =
    commonAkkaSupportedVersion

  override def akkaStream: SupportedVersion = majors("2").and(minors("6")).and(patches("8"))

}
