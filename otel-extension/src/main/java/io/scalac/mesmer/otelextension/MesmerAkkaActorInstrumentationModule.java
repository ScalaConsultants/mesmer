package io.scalac.mesmer.otelextension;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.scalac.mesmer.agent.akka.actor.AkkaActorAgent;

import java.util.List;

@AutoService(InstrumentationModule.class)
public class MesmerAkkaActorInstrumentationModule extends InstrumentationModule {
    public MesmerAkkaActorInstrumentationModule() {
        super("mesmer-akka-actor");
    }

    @Override
    public List<TypeInstrumentation> typeInstrumentations() {
        return AkkaActorAgent.agent().asOtelTypeInstrumentations();
    }
}
