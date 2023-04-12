package io.scalac.mesmer.otelextension.akka;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.scalac.mesmer.otelextension.instrumentations.akka.stream.AkkaStreamAgents;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class MesmerAkkaStreamInstrumentationModule extends InstrumentationModule {

  public MesmerAkkaStreamInstrumentationModule() {
    super("mesmer-akka-stream");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return AkkaStreamAgents.getAllStreamInstrumentations();
  }

  @Override
  public boolean isHelperClass(String className) {
    if (className.matches("io.scalac.mesmer.otelextension.instrumentations.akka.common.*")
        || className.matches("io.scalac.mesmer.otelextension.instrumentations.akka.stream.*")
        || List.of(
                "akka.ConnectionOtelOps$",
                "akka.stream.GraphLogicOtelOps$GraphLogicEnh$",
                "akka.stream.GraphLogicOtelOps$GraphLogicEnh",
                "akka.stream.GraphLogicOtelOps$",
                "io.scalac.mesmer.otelextension.instrumentations.akka.stream.AkkaStreamMonitorExtension",
                "io.scalac.mesmer.otelextension.instrumentations.akka.stream.impl.ActorGraphInterpreterOtelDecorator")
            .contains(className)) {
      return true;
    } else {
      return super.isHelperClass(className);
    }
  }

  @Override
  public List<String> getAdditionalHelperClassNames() {
    return List.of(
        "io.scalac.mesmer.otelextension.instrumentations.akka.stream.impl.GraphStageIslandOps",
        "io.scalac.mesmer.otelextension.instrumentations.akka.stream.impl.GraphStageIslandOps$",
        "akka.stream.GraphInterpreterOtelPullAdvice$",
        "akka.stream.GraphInterpreterOtelPushAdvice$");
  }
}
