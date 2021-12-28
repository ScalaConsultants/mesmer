package io.scalac.mesmer.otel_agent_extension;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;

import java.util.List;

import static java.util.Collections.singletonList;

@AutoService(InstrumentationModule.class)
public class OtelAgentExtensionInstrumentationModule extends InstrumentationModule {
    protected OtelAgentExtensionInstrumentationModule() {
        super("akka-http-mesmer", "akka-http-mesmer-metrics");
    }

    @Override
    public List<TypeInstrumentation> typeInstrumentations() {
        return singletonList(new OtelAgentExtensionInstrumentation());
    }
}
