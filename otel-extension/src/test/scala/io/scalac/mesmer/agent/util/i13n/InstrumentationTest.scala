package io.scalac.mesmer.agent.util.i13n

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation
import net.bytebuddy.matcher.ElementMatchers
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import io.scalac.mesmer.core.typeclasses.Encode._

class InstrumentationTest extends AnyFlatSpec with Matchers {

  behavior of "TypeInstrumentation"

  private val instrumentedType: TypeDesc = ElementMatchers.named("Foo")

  private val adviceExample = Advice(ElementMatchers.named("fooFunction"), "AdviceClass")

  private val otherAdviceExample = Advice("barFunction", "AdviceClass")

  it should "be equal if both otel instrumentations are the same" in {
    val first  = Instrumentation(instrumentedType).`with`(adviceExample)
    val second = Instrumentation(instrumentedType).`with`(adviceExample)

    first should be(second)
  }

  it should "notice that two instrumentations are different" in {
    Instrumentation(instrumentedType).`with`(adviceExample) should not(
      be(Instrumentation("Bar").`with`(adviceExample))
    )

    Instrumentation(instrumentedType).`with`(adviceExample) should not(
      be(Instrumentation(instrumentedType).`with`(otherAdviceExample))
    )

    Instrumentation(instrumentedType).`with`(adviceExample) should not(
      be(Instrumentation(instrumentedType).`with`(Advice("fooFunction", "OtherAdviceClass")))
    )
  }

  it should "encode to otel typeInstrumentation without errors" in {
    val instrumentation =
      Instrumentation(instrumentedType).`with`(adviceExample)

    instrumentation.encode[TypeInstrumentation]
  }

  it should "not add two the same advice twice" in {
    val instrumentation =
      Instrumentation(instrumentedType).`with`(adviceExample).`with`(adviceExample)

    instrumentation.adviceSet.size should be(1)
  }

  it should "add 2 different advice" in {
    val instrumentation =
      Instrumentation(instrumentedType)
        .`with`(adviceExample)
        .`with`(otherAdviceExample)

    instrumentation.adviceSet.size should be(2)
  }

  it should "TypeInstrumentation.typeMatcher() should equal to Instrumentation.instrumentedType" in {
    val instrumentation =
      Instrumentation(instrumentedType).`with`(adviceExample)

    val encoded = instrumentation.encode[TypeInstrumentation]

    encoded.typeMatcher() should be(instrumentedType)
  }
}
