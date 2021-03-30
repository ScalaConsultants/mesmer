package io.scalac.agent.util.i13n

import io.scalac.core.model.{ Module, SupportedModules, SupportedVersion }

trait InstrumentModuleFactory {

  def moduleName: Module
  def version: SupportedVersion
  protected def supportedModules: SupportedModules = SupportedModules(moduleName, version)

  def instrument(tpe: Type): InstrumentType = new InstrumentType(tpe, supportedModules)

}
