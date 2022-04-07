package io.scalac.mesmer.agent.util.i13n

import net.bytebuddy.description.`type`.TypeDescription
import net.bytebuddy.description.method.MethodDescription
import net.bytebuddy.matcher.ElementMatchers

import io.scalac.mesmer.agent.Agent
import io.scalac.mesmer.agent.util.i13n.InstrumentationDSL.NameDSL
import io.scalac.mesmer.agent.util.i13n.InstrumentationDetails.FQCN
import io.scalac.mesmer.core.module.Module

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
}

abstract class InstrumentModuleFactory[M <: Module](val module: M) extends InstrumentationDSL {
  this: M#All[Agent] =>

  protected def instrument(t: Type): TypeInstrumentation = TypeInstrumentation.instrument(t)

  def agent: Agent

  implicit def enrichStringDsl(value: String): InstrumentModuleFactory.StringDlsOps =
    new InstrumentModuleFactory.StringDlsOps(value -> module)

}
