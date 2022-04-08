package io.scalac.mesmer.agent.config
import io.scalac.mesmer.core.module._

import java.util
import java.util.Locale
import scala.jdk.CollectionConverters._

object MesmerConfigPropertySourceProvider {

  private def modules = Seq(
    AkkaActorModule,
    AkkaStreamModule,
    AkkaClusterModule,
    AkkaPersistenceModule,
    AkkaActorSystemModule
  )

  /**
   * Add modules has a default object that we turn to series of key-value pairs
   * @return
   */
  def getProperties: util.Map[String, String] = modules.flatMap { module =>
    module.defaultConfig.productElementNames.zip(module.defaultConfig.productIterator).map { case (key, value) =>
      // replace all camel case with dots between words
      val normalizedKey = key.replaceAll("([a-z])([A-Z])", "$1.$2").toLowerCase(Locale.ROOT)
      s"${module.configurationBase}.$normalizedKey" -> value.toString
    }
  }.toMap.asJava
}
