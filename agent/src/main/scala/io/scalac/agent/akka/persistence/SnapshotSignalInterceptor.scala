package io.scalac.agent.akka.persistence
class SnapshotSignalInterceptor
import akka.actor.typed.Signal
import akka.persistence.typed.SnapshotCompleted

case class SnapshotSignalHandler[O] private (
  private var inner: PartialFunction[(Any, Signal), Any] = PartialFunction.empty
) extends PartialFunction[(Any, Signal), O] {

  override def apply(v1: (Any, Signal)): O =
    v1 match {
      case (_, SnapshotCompleted(meta)) => {
        /*
         * We should depend on this function to produce result only if this is a unit type
         * other than that inner function will be defined an we can depend on it's result type
         */
        var result: O = ().asInstanceOf[O]
        println(s"Completed snapshot for ${meta.persistenceId}")
        if (inner.isDefinedAt(v1)) {
          result = inner.asInstanceOf[PartialFunction[(Any, Signal), O]](v1)
        }
        result
      }
      case _ => inner.asInstanceOf[PartialFunction[(Any, Signal), O]](v1)
    }

  override def isDefinedAt(x: (Any, Signal)): Boolean = x match {
    case (_, SnapshotCompleted(_)) => true
    case _                         => inner.isDefinedAt(x)
  }

  override def andThen[C](k: O => C): PartialFunction[(Any, Signal), C] = {
    inner = inner.asInstanceOf[PartialFunction[(Any, Signal), O]].andThen(k)
    this.asInstanceOf[PartialFunction[(Any, Signal), C]]
  }

  override def andThen[C](k: PartialFunction[O, C]): PartialFunction[(Any, Signal), C] = {
    inner = inner.asInstanceOf[PartialFunction[(Any, Signal), O]].andThen(k)
    this.asInstanceOf[PartialFunction[(Any, Signal), C]]
  }
}

object SnapshotSignalInterceptor {

  def constructorAdvice(pf: PartialFunction[(Any, Signal), Unit]): PartialFunction[(Any, Signal), Unit] =
    pf match {
      case _: SnapshotSignalHandler[_] =>
        println("Protected from nested publishing code")
        pf
      case _ =>
        println("Creating new SnapshotSignalHandler")
        SnapshotSignalHandler[Unit](pf)
    }

}
