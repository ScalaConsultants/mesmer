package io.scalac.agent

import java.lang.instrument.Instrumentation

import io.scalac.agent.Agent.LoadingResult
import net.bytebuddy.agent.builder.AgentBuilder

case class ModuleInformation(moduleId: String, version: String)

object Agent {
  class LoadingResult(val fqns: Seq[String]) { self =>
    def eagerLoad(): Unit =
      fqns.foreach { className =>
        try {
          Thread.currentThread().getContextClassLoader.loadClass(className)
        } catch {
          case _: ClassNotFoundException => println(s"Couldn't load class ${className}")
        }

      }
    def ++(other: LoadingResult): LoadingResult = new LoadingResult(self.fqns ++ other.fqns)
  }
  object LoadingResult {
    implicit def fromSeq(fqns: Seq[String]): LoadingResult = new LoadingResult(fqns)
  }
}

final case class Agent(installOn: (AgentBuilder, Instrumentation) => LoadingResult) { self =>

  def ++(that: Agent): Agent =
    Agent { (builder, instrumentation) =>
      self.installOn(builder, instrumentation) ++ that.installOn(builder, instrumentation)
    }
}
