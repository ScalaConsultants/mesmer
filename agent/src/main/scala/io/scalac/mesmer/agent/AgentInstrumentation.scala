package io.scalac.mesmer.agent

import net.bytebuddy.agent.builder.AgentBuilder

import io.scalac.mesmer.agent.Agent.LoadingResult
import io.scalac.mesmer.agent.util.i13n.InstrumentationDetails
import io.scalac.mesmer.agent.util.i13n.TypeInstrumentation

final case class AgentInstrumentation(
  typeInstrumentation: TypeInstrumentation,
  private val deferred: Boolean,
  load: Seq[String] = Seq.empty[String]
) extends Equals
    with Ordered[AgentInstrumentation] {

  val mesmerAgentInstallation: (AgentBuilder, java.lang.instrument.Instrumentation) => LoadingResult = {
    val instrumentationDetails: InstrumentationDetails[_] = typeInstrumentation.`type`.name
    (agentBuilder, instrumentation) =>
      agentBuilder
        .`type`(typeInstrumentation.`type`.desc)
        .transform { (underlying, _, _, _) =>
          typeInstrumentation.transformBuilder(underlying)
        }
        .installOn(instrumentation)
      if (instrumentationDetails.isFQCN) LoadingResult(instrumentationDetails.name +: load) else LoadingResult.empty
  }

  override def hashCode(): Int = typeInstrumentation.`type`.name.name.hashCode

  override def canEqual(that: Any): Boolean = that.isInstanceOf[AgentInstrumentation]

  override def equals(obj: Any): Boolean = obj match {
    case that: AgentInstrumentation if that.canEqual(this) =>
      val thisName = this.typeInstrumentation.`type`.name
      val thatName = that.typeInstrumentation.`type`.name

      thisName == thatName

    case _ => false
  }

  def compare(that: AgentInstrumentation): Int = Ordering[Boolean].compare(this.deferred, that.deferred)
}

object AgentInstrumentation {

  def apply(typeInstrumentation: TypeInstrumentation) = new AgentInstrumentation(typeInstrumentation, false)

  def withLoad(instrumentation: TypeInstrumentation, fqcns: String*): AgentInstrumentation =
    new AgentInstrumentation(instrumentation, false, fqcns)

  def deferred(instrumentation: TypeInstrumentation): AgentInstrumentation =
    new AgentInstrumentation(instrumentation, true)
}
