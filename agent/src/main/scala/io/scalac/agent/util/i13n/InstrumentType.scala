package io.scalac.agent.util.i13n

import io.scalac.agent.Agent.LoadingResult
import io.scalac.agent.AgentInstrumentation
import io.scalac.core.model.SupportedModules

final class InstrumentType(tpe: Type, modules: SupportedModules) {

  def apply(buildFn: Builder => Unit): AgentInstrumentation =
    AgentInstrumentation(tpe.name, modules) { (agentBuilder, instrumentation, _) =>
      agentBuilder
        .`type`(tpe.desc)
        .transform { (underlying, _, _, _) =>
          Builder.build(underlying, buildFn)
        }
        .installOn(instrumentation)
      LoadingResult(tpe.name)
    }

}
