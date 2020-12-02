package io.scalac.agent

import java.lang.instrument.Instrumentation

import io.scalac.agent.Agent.LoadingResult
import io.scalac.agent.util.ModuleInfo.Modules
import net.bytebuddy.agent.builder.AgentBuilder

object Agent {
  class LoadingResult(val fqns: Seq[String]) {
    def eagerLoad(): Unit =
      fqns.foreach { className =>
        try {
          Thread.currentThread().getContextClassLoader.loadClass(className)
        } catch {
          case _: ClassNotFoundException => println(s"Couldn't load class ${className}")
        }
      }

    def ++(other: LoadingResult): LoadingResult = new LoadingResult(this.fqns ++ other.fqns)
  }

  object LoadingResult {
    def apply(fqns: Seq[String]): LoadingResult = new LoadingResult(fqns)

    def apply(fqn: String, fqns: String*): LoadingResult = apply(fqn +: fqns)

    def empty: LoadingResult = new LoadingResult(Seq.empty)
  }
}

final case class Agent(installOn: (AgentBuilder, Instrumentation, Modules) => LoadingResult) { self =>

  def ++(that: Agent): Agent =
    Agent { (builder, instrumentation, moduleInfo) =>
      self.installOn(builder, instrumentation, moduleInfo) ++ that.installOn(builder, instrumentation, moduleInfo)
    }
}
