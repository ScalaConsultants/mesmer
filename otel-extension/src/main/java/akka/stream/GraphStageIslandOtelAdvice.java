package akka.stream;

import akka.stream.Attributes;
import akka.stream.Shape;
import akka.stream.impl.StreamLayout;
import io.scalac.mesmer.agentcopy.akka.stream.impl.GraphStageIslandOps;
import net.bytebuddy.asm.Advice;

import java.util.ArrayList;

public class GraphStageIslandOtelAdvice {

  @Advice.OnMethodEnter
  public static void materializeAtomic(
      @Advice.Argument(0) StreamLayout.AtomicModule<Shape, Object> mod,
      @Advice.Argument(value = 1, readOnly = false) Attributes attributes,
      @Advice.FieldValue("logics") ArrayList<?> logics) {
    attributes = GraphStageIslandOps.markLastSink(mod.shape(), attributes, logics.size());
  }
}
