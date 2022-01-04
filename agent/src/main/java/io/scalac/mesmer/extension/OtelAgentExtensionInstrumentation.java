package io.scalac.mesmer.extension;

import io.opentelemetry.instrumentation.api.config.Config;
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
        Config conf = Config.get();
        System.out.println("OtelAgentExtensionInstrumentation: " + conf.getString("otel.mesmer.someprop"));

        Boolean shouldInstrument = conf.getBoolean("otel.mesmer.akkahttp");
        if (shouldInstrument != null && shouldInstrument) {
            typeTransformer.applyAdviceToMethod(
                    named("bindAndHandle"),
                    "io.scalac.mesmer.agent.akka.http.HttpExtConnectionsAdvice");
        }
    }
}
