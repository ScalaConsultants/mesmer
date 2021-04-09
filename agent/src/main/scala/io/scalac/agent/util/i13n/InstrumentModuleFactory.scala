package io.scalac.agent.util.i13n
import io.scalac.core.model.SupportedModules

trait InstrumentModuleFactory {

  protected def supportedModules: SupportedModules

  def instrument(tpe: Type): TypeInstrumentation = TypeInstrumentation(TypeTarget(tpe, supportedModules))

}
