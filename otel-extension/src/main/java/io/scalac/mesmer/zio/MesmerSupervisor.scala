package io.scalac.mesmer.zio

import zio.Exit
import zio.Fiber
import zio.Supervisor
import zio.Trace
import zio.UIO
import zio.ZEnvironment
import zio.ZIO

class MesmerSupervisor extends Supervisor {

  override def value(implicit trace: Trace): UIO[Nothing] = ???

  override def unsafeOnStart[R, E, A](
    environment: ZEnvironment[R],
    effect: ZIO[R, E, A],
    parent: Option[Fiber.Runtime[Any, Any]],
    fiber: Fiber.Runtime[E, A]
  ): Unit = {

    MesmerFiberInstrumentation.witnessFiberStart(fiber)
    println("onStart")
  }

  override def unsafeOnEnd[R, E, A](value: Exit[E, A], fiber: Fiber.Runtime[E, A]): Unit = {
    println("onEnd")
    MesmerFiberInstrumentation.witnessFiberEnd(fiber)
  }

  override def unsafeOnEffect[E, A](fiber: Fiber.Runtime[E, A], effect: ZIO[_, _, _]): Unit = {}
  // println("onEffect")

  override def unsafeOnSuspend[E, A](fiber: Fiber.Runtime[E, A]): Unit =
    MesmerFiberInstrumentation.witnessFiberSuspension()

  override def unsafeOnResume[E, A](fiber: Fiber.Runtime[E, A]): Unit =
    MesmerFiberInstrumentation.witnessFiberResumption()
}
