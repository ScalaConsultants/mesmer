package io.scalac.mesmer.agent.util.i13n

import io.scalac.mesmer.agent.Agent.LoadingResult
import io.scalac.mesmer.agent.AgentInstrumentation

object AgentInstrumentationFactory {

  def apply(typeInstrumentation: TypeInstrumentation, load: Seq[String], deferred: Boolean): AgentInstrumentation = {
    val instrumentationDetails: InstrumentationDetails[_] = typeInstrumentation.`type`.name
    AgentInstrumentation(instrumentationDetails.name, instrumentationDetails.tags, deferred) {
      (agentBuilder, instrumentation) =>
        agentBuilder
          .`type`(typeInstrumentation.`type`.desc)
          .transform { (underlying, _, _, _) =>
            typeInstrumentation.transformBuilder(underlying)
          }
          .installOn(instrumentation)
        if (instrumentationDetails.isFQCN) LoadingResult(instrumentationDetails.name +: load) else LoadingResult.empty
    }
  }

}
