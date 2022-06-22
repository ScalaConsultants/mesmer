package io.scalac.mesmer.zio;

import net.bytebuddy.asm.Advice;
import zio.ZIO;

public class ZIOUnsafeRunAdvice {

  @Advice.OnMethodEnter
  public static void unsafeRunWithRefs(@Advice.Argument(value = 0, readOnly = false) ZIO zio) {
    if (!ZIOSupervision.supervisorAlreadyAdded()) {
      System.out.println("Adding a Mesmer supervisor");
      zio = ZIOSupervision.addMesmerSupervisor(zio);
      ZIOSupervision.setAlreadyAdded();
    } else {
      System.out.println("supervisor was already added");
    }
  }
}
