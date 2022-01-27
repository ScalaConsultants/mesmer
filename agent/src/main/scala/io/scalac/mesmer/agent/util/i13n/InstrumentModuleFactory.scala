package io.scalac.mesmer.agent.util.i13n
import com.typesafe.config.Config
import net.bytebuddy.description.`type`.TypeDescription
import net.bytebuddy.description.method.MethodDescription
import net.bytebuddy.matcher.ElementMatchers

import io.scalac.mesmer.agent.Agent
import io.scalac.mesmer.agent.util.i13n.InstrumentationDSL.NameDSL
import io.scalac.mesmer.agent.util.i13n.InstrumentationDetails.FQCN
import io.scalac.mesmer.core.model.Version
import io.scalac.mesmer.core.module.MesmerModule
import io.scalac.mesmer.core.module.Module
import io.scalac.mesmer.core.module.RegistersGlobalConfiguration
import io.scalac.mesmer.core.util.LibraryInfo.LibraryInfo
object InstrumentationDSL {
  final class NameDSL(private val value: String) extends AnyVal {
    def method: MethodDesc = ElementMatchers.named[MethodDescription](value)
    def `type`: TypeDesc   = ElementMatchers.named[TypeDescription](value)
  }
}

sealed trait InstrumentationDSL {

  protected def named(name: String): NameDSL = new NameDSL(name)
}

object InstrumentModuleFactory {
  protected class StringDlsOps(private val value: (String, Module)) extends AnyVal {

    /**
     * Assigns `tags` and module name to tags to distinguish this instrumentation from those from other modules and this
     * one and mark it as fully qualified class name
     */
    def fqcnWithTags(tags: String*): InstrumentationDetails[FQCN] =
      InstrumentationDetails.fqcn(value._1, Set(value._2.name) ++ tags)

    /**
     * Assigns module name to tags to distinguish this instrumentation from those from other modules and mark it as
     * fully qualified class name
     */
    def fqcn: InstrumentationDetails[FQCN] = InstrumentationDetails.fqcn(value._1, Set(value._2.name))

  }

  implicit class FactoryOps[M <: MesmerModule with RegistersGlobalConfiguration](
    private val factory: InstrumentModuleFactory[M]
  ) extends AnyVal {

    def defaultAgent(jarsInfo: LibraryInfo): Agent =
      factory
        .initAgent(jarsInfo, factory.module.defaultConfig, registerGlobal = false)
        .getOrElse(throw new IllegalStateException("Unsupported library version found - cannot install agent"))
  }
}

abstract class InstrumentModuleFactory[M <: Module with RegistersGlobalConfiguration](val module: M)
    extends InstrumentationDSL {
  /*
    Requiring all features to be a function from versions to Option[Agent] we allow there to create different instrumentations depending
    on runtime version of jars. TODO add information on which versions are supported
   */
  this: M#All[M#AkkaJar[Version] => Option[Agent]] =>

  protected def instrument(tpe: Type): TypeInstrumentation = TypeInstrumentation(tpe)

  /**
   * @param config
   *   configuration of features that are wanted by the user
   * @param jars
   *   versions of required jars to deduce which features can be enabled
   * @return
   *   Resulting agent and resulting configuration based on runtime properties
   */
  protected def agent(config: module.All[Boolean], jars: module.AkkaJar[Version]): (Agent, module.All[Boolean])

  private[i13n] final def agent(
    config: module.All[Boolean],
    jars: module.AkkaJar[Version],
    registerGlobal: Boolean
  ): Agent = {
    val (agents, enabled) = agent(config, jars)
    if (registerGlobal)
      module.registerGlobal(enabled)
    agents
  }

  final def initAgent(jarsInfo: LibraryInfo, config: Config): Option[Agent] =
    initAgent(jarsInfo, module.enabled(config), registerGlobal = true)

  final def initAgent(jarsInfo: LibraryInfo, config: module.All[Boolean], registerGlobal: Boolean): Option[Agent] =
    module.jarsFromLibraryInfo(jarsInfo).map { jars =>
      agent(config, jars, registerGlobal)
    }

  implicit def enrichStringDsl(value: String): InstrumentModuleFactory.StringDlsOps =
    new InstrumentModuleFactory.StringDlsOps(value -> module)

}
