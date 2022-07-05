package io.scalac.mesmer.agent.util.i13n

import net.bytebuddy.matcher.ElementMatchers
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import io.scalac.mesmer.agent.util.i13n.AdviceApplication.advice

class OtelTypeInstrumentationTest extends AnyFlatSpec with Matchers {

  behavior of "TypeInstrumentation"

  private val instrumentedType: TypeDesc = ElementMatchers.named("Foo")

  private val adviceExample = advice(ElementMatchers.named("fooFunction"), "AdviceClass")

  private val otherAdviceExample = advice("barFunction", "AdviceClass")

  it should "be equal if both otel instrumentations are the same" in {
    val first  = OtelTypeInstrumentation.instrument(instrumentedType).using(adviceExample)
    val second = OtelTypeInstrumentation.instrument(instrumentedType).using(adviceExample)

    first should be(second)
  }

  it should "notice that two instrumentations are different" in {
    OtelTypeInstrumentation.instrument(instrumentedType).using(adviceExample) should not(
      be(OtelTypeInstrumentation.instrument("Bar").using(adviceExample))
    )

    OtelTypeInstrumentation.instrument(instrumentedType).using(adviceExample) should not(
      be(OtelTypeInstrumentation.instrument(instrumentedType).using(otherAdviceExample))
    )

    OtelTypeInstrumentation.instrument(instrumentedType).using(adviceExample) should not(
      be(OtelTypeInstrumentation.instrument(instrumentedType).using(advice("fooFunction", "OtherAdviceClass")))
    )
  }

  it should "encode to otel typeInstrumentation without errors" in {
    val instrumentation =
      OtelTypeInstrumentation.instrument(instrumentedType).using(adviceExample)

    instrumentation.encode()
  }

  it should "not add two the same advice twice" in {
    val instrumentation =
      OtelTypeInstrumentation.instrument(instrumentedType).using(adviceExample).using(adviceExample)

    instrumentation.adviceSet.size should be(1)
  }

  it should "add 2 different advice" in {
    val instrumentation =
      OtelTypeInstrumentation
        .instrument(instrumentedType)
        .using(adviceExample)
        .using(otherAdviceExample)

    instrumentation.adviceSet.size should be(2)
  }

  it should "TypeInstrumentation.typeMatcher() should equal to OtelTypeInstrumentation.instrumentedType" in {
    val instrumentation =
      OtelTypeInstrumentation.instrument(instrumentedType).using(adviceExample)

    val encoded = instrumentation.encode()

    encoded.typeMatcher() should be(instrumentedType)
  }
}
