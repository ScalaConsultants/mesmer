package io.scalac.mesmer.agent.util.i13n

import io.scalac.mesmer.agent.Agent.LoadingResult
import io.scalac.mesmer.agent.AgentInstrumentation

object AgentInstrumentationFactory {

  def apply(typeInstrumentation: TypeInstrumentation): AgentInstrumentation =
    AgentInstrumentation(typeInstrumentation.target.tpe.name, typeInstrumentation.target.modules) {
      (agentBuilder, instrumentation, _) =>
        agentBuilder
          .`type`(typeInstrumentation.target.tpe.desc)
          .transform { (underlying, _, _, _) =>
            typeInstrumentation.transformBuilder(underlying)
          }
          .installOn(instrumentation)
        LoadingResult(typeInstrumentation.target.tpe.name)
    }

}
