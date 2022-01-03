package io.scalac.mesmer.extension;

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
//
//        AgentBuilder.Transformer.ForAdvice bindAndHandle = new AgentBuilder.Transformer.ForAdvice()
//                .include(getClass().getClassLoader())
//                .advice(
//                        named("bindAndHandle"),
//                        "io.scalac.mesmer.agent.akka.http.HttpExtConnectionsAdvice"
//                );
//
//        typeTransformer.applyTransformer(bindAndHandle);

        typeTransformer.applyAdviceToMethod(
                named("bindAndHandle"),
                "io.scalac.mesmer.agent.akka.http.HttpExtConnectionsAdvice");
    }


}
