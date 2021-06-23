package io.scalac.mesmer.agent.util.i13n
import com.typesafe.config.Config
import io.scalac.mesmer.agent.{ Agent, AgentInstrumentation }
import io.scalac.mesmer.core.model.SupportedModules
import io.scalac.mesmer.core.module.Module

trait InstrumentModuleFactory {

  protected def supportedModules: SupportedModules

  protected def instrument(tpe: Type): TypeInstrumentation = TypeInstrumentation(TypeTarget(tpe, supportedModules))
}

abstract class InstrumentModuleFactoryTest[M <: Module](val module: M) {
  this: M#All[Agent] =>

  protected def supportedModules: SupportedModules

  protected def instrument(tpe: Type): TypeInstrumentation = TypeInstrumentation(TypeTarget(tpe, supportedModules))

  def agent(config: M#All[Boolean]): Agent

  final def agent(config: Config): Agent = agent(module.enabled(config))
}
