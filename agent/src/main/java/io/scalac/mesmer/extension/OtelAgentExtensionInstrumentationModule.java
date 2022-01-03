package io.scalac.mesmer.extension;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.HelperResourceBuilder;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.scalac.mesmer.agent.akka.http.HttpInstrumentation;

import java.util.List;

import static java.util.Collections.singletonList;


// This is java because otherwise we can't use the autoService
@AutoService(InstrumentationModule.class)
public class OtelAgentExtensionInstrumentationModule extends InstrumentationModule {
    public OtelAgentExtensionInstrumentationModule() {
        super("akka-http", "akka-http");
    }

    @Override
    public List<TypeInstrumentation> typeInstrumentations() {
        return singletonList(new OtelAgentExtensionInstrumentation());
    }
}
