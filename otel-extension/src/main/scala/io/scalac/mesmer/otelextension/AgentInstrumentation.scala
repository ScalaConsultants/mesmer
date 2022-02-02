package io.scalac.mesmer.otelextension

import io.opentelemetry.javaagent.extension.instrumentation.{ TypeInstrumentation, TypeTransformer }
import io.scalac.mesmer.instrumentations.akka.http.Instrumentation
import io.scalac.mesmer.instrumentations.akka.http.Instrumentation.InstrumentationDetails
import net.bytebuddy.agent.builder.AgentBuilder
import net.bytebuddy.description.`type`.TypeDescription
import net.bytebuddy.dynamic.DynamicType
import net.bytebuddy.matcher.ElementMatcher
import org.slf4j.LoggerFactory

final case class AgentInstrumentation(
  typeInstrumentation: Instrumentation.TypeInstrumentation,
  deferred: Boolean,
  load: Seq[String] = Seq.empty[String] // TODO: This could be a non-primitive
) extends Equals
    with Ordered[AgentInstrumentation] {

  val otelInstallation: TypeInstrumentation =
    new TypeInstrumentation {
      override def typeMatcher(): ElementMatcher[TypeDescription] = typeInstrumentation.`type`.desc

      override def transform(transformer: TypeTransformer): Unit =
        transformer.applyTransformer { (builder: DynamicType.Builder[_], _, _, _) =>
          typeInstrumentation.transformBuilder(builder)
        }
    }

  val mesmerInstallation: (AgentBuilder, java.lang.instrument.Instrumentation) => LoadingResult = {
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

  override def hashCode(): Int = typeInstrumentation.`type`.name.hashCode()

  override def canEqual(that: Any): Boolean = that.isInstanceOf[AgentInstrumentation]

  override def equals(obj: Any): Boolean = obj match {
    case that: AgentInstrumentation if that.canEqual(this) =>
      this.typeInstrumentation.`type`.name == that.typeInstrumentation.`type`.name
    case _ => false
  }
  def compare(that: AgentInstrumentation): Int = Ordering[Boolean].compare(this.deferred, that.deferred)
}

class LoadingResult(val fqns: Set[String]) {

  import LoadingResult.{ logger => loadingLogger }

  def eagerLoad(): Unit =
    fqns.foreach { className =>
      try Thread.currentThread().getContextClassLoader.loadClass(className)
      catch {
        case _: ClassNotFoundException => loadingLogger.error("Couldn't load class {}", className)
      }
    }

  def ++(other: LoadingResult): LoadingResult = new LoadingResult(this.fqns ++ other.fqns)

  override def hashCode(): Int = fqns.hashCode()

  override def equals(obj: Any): Boolean = obj match {
    case loadingResult: LoadingResult => loadingResult.fqns == this.fqns
    case _                            => false
  }

}

object LoadingResult {
  private val logger = LoggerFactory.getLogger(classOf[LoadingResult])

  def apply(fqns: Seq[String]): LoadingResult = new LoadingResult(fqns.toSet)

  def apply(fqn: String, fqns: String*): LoadingResult = apply(fqn +: fqns)

  def empty: LoadingResult = new LoadingResult(Set.empty)
}
