package io.scalac.agent.util.i13n

import io.scalac.core.model.{ Module, SupportedModules, SupportedVersion }

trait InstrumentModuleFactory {

  def moduleName: Module
  def version: SupportedVersion
  protected def supportedModules: SupportedModules = SupportedModules(moduleName, version)

  def instrument(typeName: String): InstrumentType  = instrument(`type`(typeName))
  def instrument(typeDescOps: Type): InstrumentType = new InstrumentType(typeDescOps, supportedModules)

}
