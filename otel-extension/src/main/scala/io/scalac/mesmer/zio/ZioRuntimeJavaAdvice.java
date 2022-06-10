package io.scalac.mesmer.zio;

import java.util.Objects;
import net.bytebuddy.asm.Advice;
import zio.ZIO;

public class ZioRuntimeJavaAdvice {

  @Advice.OnMethodEnter
  public static <R, E, A> void unsafeRun(
      @Advice.Argument(value = 0, readOnly = false) ZIO<R, E, A> zio,
      @Advice.Argument(1) Object trace) {

    System.out.println(trace.toString());

    // This is a silly hack:
    // we don't want other user's unsafeRun invocations to run the below code.
    // That works but requires mesmer to know about which one is the class that runs the unsafeRun
    // in the Main method.
    if (Objects.equals(trace.toString(), "com.lgajowy.ziominer.Main.main(Main.scala:9)")) {
      System.out.println("I was applied ");
      zio = ZIOMetricsInstrumenter.trackZIOFiberCount(zio);
    }
  }
}
