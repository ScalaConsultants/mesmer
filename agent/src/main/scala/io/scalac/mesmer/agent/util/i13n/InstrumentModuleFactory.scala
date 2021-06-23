package io.scalac.mesmer.agent.util.i13n
import com.typesafe.config.Config
import io.scalac.mesmer.agent.Agent
import io.scalac.mesmer.agent.util.i13n.InstrumentationDetails.FQCN
import io.scalac.mesmer.core.model.SupportedModules
import io.scalac.mesmer.core.module.{MesmerModule, Module}

object InstrumentModuleFactory {
  protected class StringDlsOps(private val value: (String, Module)) extends AnyVal {

    /**
     * Assigns `tags` and module name to tags to distinguish this instrumentation from those from other modules and this one
     * and mark it as fully qualified class name
     */
    def fqcnWithTags(tags: String*): InstrumentationDetails[FQCN] =
      InstrumentationDetails.fqcn(value._1, Set(value._2.name) ++ tags)

    /**
     * Assigns module name to tags to distinguish this instrumentation from those from other modules
     * and mark it as fully qualified class name
     */
    def fqcn: InstrumentationDetails[FQCN] = InstrumentationDetails.fqcn(value._1, Set(value._2.name))

  }

  implicit class FactoryOps[M <: MesmerModule](private val factory: InstrumentModuleFactory[M]) extends AnyVal {
    def defaultAgent: Agent = {
      factory.agent(factory.module.defaultConfig)
    }
  }
}

abstract class InstrumentModuleFactory[M <: Module](val module: M) {
  this: M#All[Agent] =>

  protected def supportedModules: SupportedModules

  protected def instrument(tpe: Type): TypeInstrumentation = TypeInstrumentation(TypeTarget(tpe, supportedModules))

  def agent(config: module.All[Boolean]): Agent

  final def agent(config: Config): Agent = agent(module.enabled(config))

  implicit def enrichStringDsl(value: String): InstrumentModuleFactory.StringDlsOps =
    new InstrumentModuleFactory.StringDlsOps(value -> module)

}
