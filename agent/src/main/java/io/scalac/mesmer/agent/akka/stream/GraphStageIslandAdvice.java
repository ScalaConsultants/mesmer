package akka.stream;

import akka.stream.impl.StreamLayout;
import io.scalac.mesmer.agent.akka.stream.GraphStageIslandOps;
import net.bytebuddy.asm.Advice;

import java.util.ArrayList;

public class GraphStageIslandAdvice {

    @Advice.OnMethodEnter
    public static void materializeAtomic(@Advice.Argument(0) StreamLayout.AtomicModule<Shape, Object> mod,
                                         @Advice.Argument(value = 1, readOnly = false) Attributes attributes,
                                         @Advice.FieldValue("logics") ArrayList<?> logics
    ) {
        attributes = GraphStageIslandOps.markLastSink(mod.shape(), attributes, logics.size());
    }
}
