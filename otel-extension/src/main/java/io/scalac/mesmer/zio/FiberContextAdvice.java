package io.scalac.mesmer.zio;

import net.bytebuddy.asm.Advice;
import zio.internal.FiberContext;

public class FiberContextAdvice {

  @Advice.OnMethodExit
  public static void foo(@Advice.This FiberContext fc) {
    System.out.println("Fiber context runUntil. Fiberid: " + fc.fiberId());
  }
}
