package zio

import zio.internal.Platform

import scala.jdk.CollectionConverters.CollectionHasAsScala

object diagnostics {

  val ZMXSupervisor: Supervisor[Set[Fiber.Runtime[Any, Any]]] =
    new Supervisor[Set[Fiber.Runtime[Any, Any]]] {

      private[this] val fibers =
        Platform.newConcurrentWeakSet[Fiber.Runtime[Any, Any]]()

      def value(implicit trace: Trace): UIO[Set[Fiber.Runtime[Any, Any]]] = ZIO.succeed(fibers.asScala.toSet)

      override def unsafeOnStart[R, E, A](
        environment: ZEnvironment[R],
        effect: ZIO[R, E, A],
        parent: Option[Fiber.Runtime[Any, Any]],
        fiber: Fiber.Runtime[E, A]
      ): Unit = fibers.add(fiber)

      override def unsafeOnEnd[R, E, A](value: Exit[E, A], fiber: Fiber.Runtime[E, A]): Unit =
        fibers.remove(fiber)
    }
}
