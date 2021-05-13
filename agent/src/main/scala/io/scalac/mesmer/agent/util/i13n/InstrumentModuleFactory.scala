package io.scalac.mesmer.agent.util.i13n
import io.scalac.mesmer.agent.Agent
import io.scalac.mesmer.core.model.SupportedModules
import net.bytebuddy.pool.TypePool

trait InstrumentModuleFactory {

  protected def supportedModules: SupportedModules

  protected def instrument(tpe: Type): TypeInstrumentation = TypeInstrumentation(TypeTarget(tpe, supportedModules))
}
