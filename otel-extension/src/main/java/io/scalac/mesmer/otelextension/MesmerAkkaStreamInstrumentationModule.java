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
import net.bytebuddy.matcher.ElementMatchers;

import java.util.*;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;

@AutoService(InstrumentationModule.class)
public class MesmerAkkaStreamInstrumentationModule extends InstrumentationModule implements InstrumentationModuleMuzzle {

    public MesmerAkkaStreamInstrumentationModule() {
        super("mesmer-akka-stream");
    }

    @Override
    public List<TypeInstrumentation> typeInstrumentations() {

        TypeInstrumentation connectionConst = new TypeInstrumentation() {
            @Override
            public ElementMatcher<TypeDescription> typeMatcher() {
                return ElementMatchers.named("akka.stream.impl.fusing.GraphInterpreter$Connection");
            }

            @Override
            public void transform(TypeTransformer transformer) {
                transformer.applyAdviceToMethod(isConstructor(), "akka.stream.ConnectionConstructorAdvice");
            }
        };


        List<TypeInstrumentation> streamInstrumentations = new ArrayList<>();

        streamInstrumentations.add(connectionConst);

        streamInstrumentations.addAll(AkkaStreamAgent.agent().asOtelTypeInstrumentations());

        return streamInstrumentations;

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


    @Override
    public List<String> getAdditionalHelperClassNames() {
        return Arrays.asList(
                "akka.stream.GraphInterpreterOtelPushAdvice$",
                "akka.stream.GraphInterpreterOtelPullAdvice$",
                "akka.stream.GraphLogicOtelOps",
                "akka.stream.GraphLogicOtelOps$",
                "akka.stream.GraphLogicOtelOps$GraphLogicEnh",
                "akka.stream.GraphLogicOtelOps$GraphLogicEnh$",
                "akka.ConnectionOtelOps",
                "akka.ConnectionOtelOps$",
                "io.scalac.mesmer.agentcopy.akka.stream.impl.ActorGraphInterpreterOtelDecorator",
                "io.scalac.mesmer.agentcopy.akka.stream.impl.ActorGraphInterpreterOtelDecorator$",
                "io.scalac.mesmer.agentcopy.akka.stream.impl.ActorGraphInterpreterOtelDecorator$$anonfun$addCollectionReceive$1",
                "akka.ActorGraphInterpreterProcessEventOtelAdvice$",
                "akka.ActorGraphInterpreterTryInitOtelAdvice$",
                "io.scalac.mesmer.agentcopy.akka.stream.impl.GraphStageIslandOps",
                "io.scalac.mesmer.agentcopy.akka.stream.impl.GraphStageIslandOps$",
                "io.scalac.mesmer.agentcopy.akka.stream.impl.GraphStageIslandOps$TerminalSink$",
                "io.scalac.mesmer.agentcopy.akka.stream.impl.PhasedFusingActorMaterializerAdvice$"
        );
    }
}
