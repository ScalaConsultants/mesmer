package io.scalac.mesmer.core.module

import org.slf4j.LoggerFactory

import scala.jdk.CollectionConverters._
import scala.util.Try

import io.scalac.mesmer.core.config.MesmerConfigurationBase
import io.scalac.mesmer.core.invoke.Lookup
import io.scalac.mesmer.core.typeclasses.Combine
import io.scalac.mesmer.core.typeclasses.Traverse

trait Module {
  def name: String
  type All[T] <: AnyRef
  type Config = All[Boolean] with Product

  def enabled: Config
}

object Module {

  implicit class AllOps[M[X] <: Module#All[X], T](private val value: M[T]) extends AnyVal {
    def combine(other: M[T])(implicit combine: Combine[M[T]]): M[T]          = combine.combine(value, other)
    def exists(check: T => Boolean)(implicit traverse: Traverse[M]): Boolean = traverse.sequence(value).exists(check)
  }
}

object MesmerModule extends Lookup {

  import java.lang.invoke.MethodHandles._
  import java.lang.invoke.MethodType._

  private val logger = LoggerFactory.getLogger(MesmerModule.getClass)

  private def getMapFromConfigClass(clazz: Class[_]): Map[String, String] = {
    val configInstanceHandle = lookup.findStatic(clazz, "get", methodType(clazz))
    val allPropertiesHandle  = lookup.findVirtual(clazz, "getAllProperties", methodType(classOf[java.util.Map[_, _]]))

    foldArguments(allPropertiesHandle, configInstanceHandle)
      .invoke()
      .asInstanceOf[java.util.Map[String, String]]
      .asScala
      .toMap
  }

  lazy val globalConfig: Map[String, String] =
    Try {
      getMapFromConfigClass(Class.forName("io.opentelemetry.javaagent.shaded.instrumentation.api.config.Config"))
    }.orElse(Try {
      getMapFromConfigClass(Class.forName("io.opentelemetry.instrumentation.api.config.Config"))
    }).getOrElse {
      logger.warn("No configuration found. Make sure that OpenTelemetry or Mesmer agent is installed.")
      Map.empty
    }

  private def parseBoolean(value: String, default: Boolean): Boolean = value.toLowerCase() match {
    case "t" | "true" | "1"  => true
    case "f" | "false" | "0" => false
    case _                   => default
  }

}

trait MesmerModule extends Module with MesmerConfigurationBase {
  override type Result = Config with Product

  lazy val enabled: Config = {

    val moduleConfigurations = MesmerModule.globalConfig.keys.collect {
      case moduleKey if moduleKey.startsWith(configurationBase) =>
        moduleKey.stripPrefix(s"$configurationBase.") -> MesmerModule.parseBoolean(moduleKey, false)
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
