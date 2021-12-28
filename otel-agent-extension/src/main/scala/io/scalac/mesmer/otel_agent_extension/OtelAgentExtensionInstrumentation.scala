package io.scalac.mesmer.otel_agent_extension

import io.opentelemetry.javaagent.extension.instrumentation.{ TypeInstrumentation, TypeTransformer }
import net.bytebuddy.asm.Advice
import net.bytebuddy.description.`type`.TypeDescription
import net.bytebuddy.matcher.ElementMatcher
import net.bytebuddy.matcher.ElementMatchers.named

class OtelAgentExtensionInstrumentation extends TypeInstrumentation {
  override def typeMatcher(): ElementMatcher[TypeDescription] =
    named("akka.http.scaladsl.HttpExt")

  override def transform(transformer: TypeTransformer): Unit =
    transformer
      .applyAdviceToMethod(
        named("bindAndHandle"),
        this.getClass.getName + "$OtelAgentExtensionAdvice"
      )
}

class OtelAgentExtensionAdvice {

  @Advice.OnMethodEnter(suppress = classOf[Nothing])
  def onEnter(): Unit =
    println("I was there and I was INJECTED by OTEL AGENT")

}
