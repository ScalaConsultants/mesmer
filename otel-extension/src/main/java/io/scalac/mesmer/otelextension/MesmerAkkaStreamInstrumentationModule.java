package io.scalac.mesmer.otelextension;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.tooling.muzzle.InstrumentationModuleMuzzle;
import io.opentelemetry.javaagent.tooling.muzzle.VirtualFieldMappingsBuilder;
import io.opentelemetry.javaagent.tooling.muzzle.references.ClassRef;
import io.scalac.mesmer.agentcopy.akka.stream.AkkaStreamAgent;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;

@AutoService(InstrumentationModule.class)
public class MesmerAkkaStreamInstrumentationModule extends InstrumentationModule implements InstrumentationModuleMuzzle {
    public MesmerAkkaStreamInstrumentationModule() {
        super("mesmer-akka-stream");
    }

    @Override
    public List<TypeInstrumentation> typeInstrumentations() {

        AkkaStreamAgent.agent()

//        return Collections.singletonList(new TypeInstrumentation() {
//            @Override
//            public ElementMatcher<TypeDescription> typeMatcher() {
//                return named("akka.stream.impl.fusing.GraphInterpreter$Connection");
//            }
//
//            @Override
//            public void transform(TypeTransformer transformer) {
//                transformer.applyAdviceToMethod(isConstructor(), "akka.ConnectionAdvice");
//            }
//        });
    }

    @Override
    public Map<String, ClassRef> getMuzzleReferences() {
        return Collections.emptyMap();
    }

    @Override
    public void registerMuzzleVirtualFields(VirtualFieldMappingsBuilder builder) {
        builder.register("akka.stream.impl.fusing.GraphInterpreter$Connection", "scala.Tuple2");
    }

    @Override
    public List<String> getMuzzleHelperClassNames() {
        return Collections.emptyList();
    }
}
