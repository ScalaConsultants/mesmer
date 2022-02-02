package io.scalac.mesmer.otelextension;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.scalac.mesmer.instrumentations.akka.http.Instrumentation;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.matcher.ElementMatcher;

import static net.bytebuddy.matcher.ElementMatchers.named;

public class AkkaHttpRequestsTypeInstrumentation implements TypeInstrumentation {

  private Instrumentation.TypeInstrumentation mesmerTypeInstrumentation;

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    // The TypeDescription type was erased, but maybe we can live with it
    return mesmerTypeInstrumentation.type().desc();
  }

  @Override
  public void transform(TypeTransformer transformer) {

    transformer.applyTransformer(
        (builder, typeDescription, classLoader, module) ->
            mesmerTypeInstrumentation.transformBuilder((DynamicType.Builder<?>) builder));
  }
}
