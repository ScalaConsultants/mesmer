package akka.stream;

import akka.stream.impl.StreamLayout;
import io.scalac.mesmer.otelextension.instrumentations.akka.stream.impl.GraphStageIslandOps;
import java.util.ArrayList;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;

public class GraphStageIslandOtelAdvice {

  @Advice.OnMethodEnter
  public static void materializeAtomic(
      @Advice.Argument(0) Object mod,
      @Advice.Argument(value = 1, readOnly = false, typing = Assigner.Typing.DYNAMIC)
          Object attributes,
      @Advice.FieldValue("logics") ArrayList<?> logics) {
    attributes =
        GraphStageIslandOps.markLastSink(
            ((StreamLayout.AtomicModule<Shape, Object>) mod).shape(),
            (Attributes) attributes,
            logics.size());
  }
}
