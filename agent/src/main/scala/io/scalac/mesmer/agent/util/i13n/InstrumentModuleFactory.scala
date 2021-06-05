package io.scalac.mesmer.agent.util.i13n
import io.scalac.mesmer.core.model.SupportedModules

trait InstrumentModuleFactory {

  protected def supportedModules: SupportedModules

  protected def instrument(tpe: Type): TypeInstrumentation = TypeInstrumentation(TypeTarget(tpe, supportedModules))
}
