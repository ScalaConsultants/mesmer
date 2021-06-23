package io.scalac.mesmer.agent.util.i13n

import io.scalac.mesmer.agent.Agent.LoadingResult
import io.scalac.mesmer.agent.AgentInstrumentation

object AgentInstrumentationFactory {

  def apply(typeInstrumentation: TypeInstrumentation): AgentInstrumentation = {
    val instrumentationName: InstrumentationName = typeInstrumentation.target.`type`.name
    AgentInstrumentation(instrumentationName.value, typeInstrumentation.target.modules) {
      (agentBuilder, instrumentation, _) =>
        agentBuilder
          .`type`(typeInstrumentation.target.`type`.desc)
          .transform { (underlying, _, _, _) =>
            typeInstrumentation.transformBuilder(underlying)
          }
          .installOn(instrumentation)
        if (instrumentationName.fqcn) LoadingResult(instrumentationName.value) else LoadingResult.empty
    }
  }

}
