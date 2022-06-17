package zio

class OtelSupervisor extends Supervisor[Unit] {
  override def value(implicit trace: Trace): UIO[Unit] = ZIO.unit

  override private[zio] def unsafeOnStart[R, E, A](
    environment: ZEnvironment[R],
    effect: ZIO[R, E, A],
    parent: Option[Fiber.Runtime[Any, Any]],
    fiber: Fiber.Runtime[E, A]
  ): Unit =
    println("OtelSupervisor.unsafeOnStart")

  override private[zio] def unsafeOnEnd[R, E, A](value: Exit[E, A], fiber: Fiber.Runtime[E, A]): Unit =
    println("OtelSupervisor.unsafeOnEnd")

  override private[zio] def unsafeOnEffect[E, A](fiber: Fiber.Runtime[E, A], effect: ZIO[_, _, _]): Unit = ()

  override private[zio] def unsafeOnSuspend[E, A](fiber: Fiber.Runtime[E, A]): Unit =
    println("OtelSupervisor.unsafeOnSuspend")

  override private[zio] def unsafeOnResume[E, A](fiber: Fiber.Runtime[E, A]): Unit =
    println("OtelSupervisor.unsafeOnResume")
}
