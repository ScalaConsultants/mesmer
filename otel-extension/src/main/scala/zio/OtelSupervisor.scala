package zio

class OtelSupervisor extends Supervisor[Unit] {
  override def value(implicit trace: Trace): UIO[Unit] = ZIO.unit

  override def onStart[R, E, A](
    environment: ZEnvironment[R],
    effect: ZIO[R, E, A],
    parent: Option[Fiber.Runtime[Any, Any]],
    fiber: Fiber.Runtime[E, A]
  )(implicit unsafe: Unsafe): Unit = println("OtelSupervisor.unsafeOnStart")

  def onEnd[R, E, A](value: Exit[E, A], fiber: Fiber.Runtime[E, A])(implicit unsafe: Unsafe): Unit =
    println("OtelSupervisor.unsafeOnEnd")

  override def onEffect[E, A](fiber: Fiber.Runtime[E, A], effect: ZIO[_, _, _])(implicit unsafe: Unsafe): Unit = ()

  override def onSuspend[E, A](fiber: Fiber.Runtime[E, A])(implicit unsafe: Unsafe): Unit =
    println("OtelSupervisor.unsafeOnSuspend")

  override def onResume[E, A](fiber: Fiber.Runtime[E, A])(implicit unsafe: Unsafe): Unit =
    println("OtelSupervisor.unsafeOnResume")
}
