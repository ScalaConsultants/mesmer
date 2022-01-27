package io.scalac.mesmer.core.module

import com.typesafe.config.{ Config => TypesafeConfig }

import io.scalac.mesmer.core.config.MesmerConfigurationBase
import io.scalac.mesmer.core.model.Version
import io.scalac.mesmer.core.module.Module.CommonJars
import io.scalac.mesmer.core.typeclasses.Combine
import io.scalac.mesmer.core.typeclasses.Traverse
import io.scalac.mesmer.core.util.LibraryInfo.LibraryInfo

trait Module {
  def name: String
  type All[T] <: AnyRef
  type Config = All[Boolean]

  def enabled(config: TypesafeConfig): Config

  type AkkaJar[T] <: CommonJars[T]

  def jarsFromLibraryInfo(info: LibraryInfo): Option[AkkaJar[Version]]
}

object Module {

  implicit class AllOps[M[X] <: Module#All[X], T](private val value: M[T]) extends AnyVal {
    def combine(other: M[T])(implicit combine: Combine[M[T]]): M[T]          = combine.combine(value, other)
    def exists(check: T => Boolean)(implicit traverse: Traverse[M]): Boolean = traverse.sequence(value).exists(check)
  }

  trait CommonJars[T] {
    def akkaActor: T
    def akkaActorTyped: T
  }

  object JarsNames {
    final val akkaActor            = "akka-actor"
    final val akkaActorTyped       = "akka-actor-typed"
    final val akkaPersistence      = "akka-persistence"
    final val akkaPersistenceTyped = "akka-persistence-typed"
    final val akkaStream           = "akka-stream"
    final val akkaHttp             = "akka-http"
    final val akkaCluster          = "akka-cluster"
    final val akkaClusterTyped     = "akka-cluster-typed"
  }

}

trait MesmerModule extends Module with MesmerConfigurationBase {
  override type Result = Config

  final def enabled(config: TypesafeConfig): Config = fromConfig(config)

  def defaultConfig: Result

  val mesmerConfig: String = s"module.$name"
}

trait MetricsModule {
  this: Module =>
  override type All[T] <: Metrics[T]
  type Metrics[T]
}

trait RegisterGlobalConfiguration extends Module {

  @volatile
  private[this] var global: All[Boolean] = _

  final def registerGlobal(conf: All[Boolean]): Unit =
    global = conf

  final def globalConfiguration: Option[All[Boolean]] = if (global ne null) Some(global) else None
}
