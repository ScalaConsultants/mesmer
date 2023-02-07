package io.scalac.mesmer.configuration

import io.opentelemetry.javaagent.bootstrap.internal.InstrumentationConfig

object Config {

  private val config = InstrumentationConfig.get()

  def getBoolean(property: String, defaultValue: Boolean): Boolean =
    config.getBoolean(property, defaultValue)

}
