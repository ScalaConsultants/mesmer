package io.scalac.mesmer.otel_agent_extension;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import static net.bytebuddy.matcher.ElementMatchers.named;


class OtelAgentExtensionInstrumentation implements TypeInstrumentation {

    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
        return named("akka.http.scaladsl.HttpExt");
    }

    @Override
    public void transform(TypeTransformer typeTransformer) {
        System.out.println(this.getClass().getName());

        typeTransformer.applyAdviceToMethod(
                named("bindAndHandle"),
                "io.scalac.mesmer.otel_agent_extension.OtelAdvice");

        System.out.println("foo2");
    }
}
