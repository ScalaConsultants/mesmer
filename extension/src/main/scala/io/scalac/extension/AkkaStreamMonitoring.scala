package io.scalac.extension

import akka.actor.ActorRef
import akka.actor.typed.receptionist.Receptionist.Register
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{Behavior, Terminated}
import io.scalac.core.akka.model.PushMetrics
import io.scalac.core.model.ConnectionStats
import io.scalac.extension.AkkaStreamMonitoring.{Command, StartStreamCollection, StatsReceived}
import io.scalac.extension.event.ActorInterpreterStats
import io.scalac.extension.metric.StreamMetricsMonitor
import io.scalac.extension.metric.StreamMetricsMonitor.Labels
import io.scalac.extension.model.Direction._
object AkkaStreamMonitoring {

  sealed trait Command

  private case class StatsReceived(actorInterpreterStats: ActorInterpreterStats) extends Command

  case class StartStreamCollection(refs: Set[ActorRef]) extends Command

  def apply(streamMonitor: StreamMetricsMonitor): Behavior[Command] =
    Behaviors.setup(ctx => new AkkaStreamMonitoring(ctx, streamMonitor))

}

class AkkaStreamMonitoring(context: ActorContext[Command], streamMonitor: StreamMetricsMonitor)
    extends AbstractBehavior[Command](context) {

  private val _context = context
  import _context._

  private[this] var connectionStats: Option[Set[ConnectionStats]] = None

  system.receptionist ! Register(streamServiceKey, messageAdapter(StatsReceived.apply))

  override def onMessage(msg: Command): Behavior[Command] = msg match {
    case StartStreamCollection(refs) if refs.nonEmpty =>
      log.info("Start stream stats collection")
      refs.foreach { ref =>
        watch(ref)
        ref ! PushMetrics
      }
      collect(refs)
    case StartStreamCollection(_) => {
      log.error(s"StartStreamCollection with empty refs")
      this
    }
    case any =>
      log.info("Received message {}", any)
      this
  }

  // TODO optimize this!
  def recordAll(): Unit =
    connectionStats.foreach { stats =>
      stats.groupBy(_.inName).foreach {
        case (inName, stats) =>
          stats
            .groupBy(_.outName)
            .view
            .mapValues(_.foldLeft(0L)(_ + _.push))
            .foreach {
              case (outName, count) =>
                val labels = Labels(None, None, inName.name, In, outName.name)

                streamMonitor.bind(labels).operatorProcessedMessages.incValue(count)
            }
      }

      stats.groupBy(_.outName).foreach {
        case (outName, stats) =>
          stats
            .groupBy(_.inName)
            .view
            .mapValues(_.foldLeft(0L)(_ + _.pull))
            .foreach {
              case (inName, count) =>
                val labels = Labels(None, None, outName.name, Out, inName.name)

                streamMonitor.bind(labels).operatorProcessedMessages.incValue(count)
            }
      }
    }

  def collect(refs: Set[ActorRef]): Behavior[Command] =
    Behaviors
      .receiveMessage[Command] {
        case StatsReceived(ActorInterpreterStats(self, connections, _)) => {
          val refsLeft = refs - self
          log.trace("Received stats from {}", self)

          unwatch(self)

          connectionStats.fold[Unit] {
            connectionStats = Some(connections)
          }(prev => connectionStats = Some(prev ++ connections))

          if (refsLeft.isEmpty) {
//            connectionStats.foreach(all => all.foreach(conn => log.debug("ConnectionStats {}", conn)))
            log.info("Finished collecting stats")
            recordAll()
            this
          } else {
            collect(refsLeft)
          }
        }
        // TODO handle this case better
        case StartStreamCollection(_) => {
          log.debug("Another collection started but previous didn't finish")
          Behaviors.same
        }
      }
      .receiveSignal {
        case (_, Terminated(ref)) => {
          log.debug("Stream ref {} terminated", ref)
          collect(refs - ref.toClassic)
        }
      }
}
