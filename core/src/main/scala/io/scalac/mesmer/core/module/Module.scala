package io.scalac.mesmer.core.module

import io.scalac.mesmer.core.config.MesmerConfigurationBase
import io.scalac.mesmer.core.model.Version
import io.scalac.mesmer.core.module.Module.CommonJars
import io.scalac.mesmer.core.typeclasses.Combine
import io.scalac.mesmer.core.typeclasses.Traverse
import io.scalac.mesmer.core.util.LibraryInfo.LibraryInfo
import io.opentelemetry.instrumentation.api.config.{ Config => OTConfig }
import scala.jdk.CollectionConverters._

trait Module {
  def name: String
  type All[T] <: AnyRef
  type Config = All[Boolean] with Product

  def enabled: Config

  type Jars[T] <: CommonJars[T]

  def jarsFromLibraryInfo(info: LibraryInfo): Option[Jars[Version]]
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
}

trait MesmerModule extends Module with MesmerConfigurationBase {
  override type Result = Config with Product

  lazy val enabled: Config = {

    val config = OTConfig.get()

    val moduleConfigurations = config.getAllProperties.asScala.keys.collect {
      case moduleKey if moduleKey.startsWith(configurationBase) =>
        moduleKey.stripPrefix(s"$configurationBase.") -> config.getBoolean(moduleKey, false)
    }.toMap

    fromMap(moduleConfigurations)
  }

  protected def fromMap(properties: Map[String, Boolean]): Config

  def defaultConfig: Result

  lazy val mesmerConfig: String = s"module.$name"
}

trait MetricsModule {
  this: Module =>
  override type All[T] <: Metrics[T]
  type Metrics[T]
}
