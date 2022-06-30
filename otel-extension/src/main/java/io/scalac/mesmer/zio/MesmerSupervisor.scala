package io.scalac.mesmer.zio

import zio.Exit
import zio.Fiber
import zio.Supervisor
import zio.Trace
import zio.UIO
import zio.Unsafe
import zio.ZEnvironment
import zio.ZIO

class MesmerSupervisor extends Supervisor {

  override def value(implicit trace: Trace): UIO[Nothing] = ???

  override def onStart[R, E, A](
    environment: ZEnvironment[R],
    effect: ZIO[R, E, A],
    parent: Option[Fiber.Runtime[Any, Any]],
    fiber: Fiber.Runtime[E, A]
  )(implicit unsafe: Unsafe): Unit = {
    MesmerFiberInstrumentation.witnessFiberStart(fiber)
    println("onStart")
  }

  override def onEnd[R, E, A](value: Exit[E, A], fiber: Fiber.Runtime[E, A])(implicit unsafe: Unsafe): Unit = {
    println("onEnd")
    MesmerFiberInstrumentation.witnessFiberEnd(fiber)
  }

  override def onEffect[E, A](fiber: Fiber.Runtime[E, A], effect: ZIO[_, _, _])(implicit unsafe: Unsafe): Unit = ()

  override def onSuspend[E, A](fiber: Fiber.Runtime[E, A])(implicit unsafe: Unsafe): Unit =
    MesmerFiberInstrumentation.witnessFiberSuspension()

  override def onResume[E, A](fiber: Fiber.Runtime[E, A])(implicit unsafe: Unsafe): Unit =
    MesmerFiberInstrumentation.witnessFiberResumption()
}
