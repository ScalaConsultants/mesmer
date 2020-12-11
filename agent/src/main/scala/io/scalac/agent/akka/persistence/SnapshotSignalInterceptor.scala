package io.scalac.agent.akka.persistence
class SnapshotSignalInterceptor
import akka.actor.typed.Signal
import akka.persistence.typed.SnapshotCompleted

//case object SnapshotSignalHandler extends PartialFunction[(Any, Signal), Unit] {
//  override def apply(v1: (Any, Signal)): Unit = {
//    case (_, SnapshotCompleted(meta)) => {
//      println(s"Completed snapshot for ${meta.persistenceId}")
//    }
//  }
//
//  override def isDefinedAt(x: (Any, Signal)): Boolean = x match {
//    case (_, SnapshotCompleted(_)) => true
//    case _                         => false
//  }
////
////  override def compose[R](k: PartialFunction[R, (Any, Signal)]): PartialFunction[R, Unit] = {
////
////  }
//}

object SnapshotSignalInterceptor {

  private val signallingFunction: PartialFunction[(Any, Signal), (Any, Signal)] = {
    case input @ (_, signal) => {
      signal match {
        case SnapshotCompleted(meta) => println(s"Completed snapshot for ${meta.persistenceId}")
        case _                       =>
      }
      input
    }
  }

  def constructorAdvice(pf: PartialFunction[(Any, Signal), Unit]): PartialFunction[(Any, Signal), Unit] = {
    println("CONSTUCTOR ADVICE")
    signallingFunction.andThen(pf)
  }

}
