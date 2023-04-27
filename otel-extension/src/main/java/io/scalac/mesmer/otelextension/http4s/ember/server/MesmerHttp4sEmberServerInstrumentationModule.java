package io.scalac.mesmer.otelextension.http4s.ember.server;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.tooling.muzzle.InstrumentationModuleMuzzle;
import io.opentelemetry.javaagent.tooling.muzzle.VirtualFieldMappingsBuilder;
import io.opentelemetry.javaagent.tooling.muzzle.references.ClassRef;
import io.scalac.mesmer.otelextension.instrumentations.http4s.ember.server.Http4sEmberServerInstrumentations;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@AutoService(InstrumentationModule.class)
public class MesmerHttp4sEmberServerInstrumentationModule extends InstrumentationModule
    implements InstrumentationModuleMuzzle {
    public MesmerHttp4sEmberServerInstrumentationModule() {
        super("mesmer-http4s-ember-server");
    }

    @Override
    public List<TypeInstrumentation> typeInstrumentations() {
        return Collections.singletonList(Http4sEmberServerInstrumentations.serverHelpersRunApp());
    }

    @Override
    public List<String> getAdditionalHelperClassNames() {
        return List.of(
            "io.scalac.mesmer.otelextension.instrumentations.http4s.ember.server.advice.ServerHelpersRunAppAdviceHelper$"
        );
    }

    @Override
    public Map<String, ClassRef> getMuzzleReferences() {
        return Collections.emptyMap();
    }

    @Override
    public void registerMuzzleVirtualFields(VirtualFieldMappingsBuilder builder) {}

    @Override
    public List<String> getMuzzleHelperClassNames() {
        return Collections.emptyList();
    }
}