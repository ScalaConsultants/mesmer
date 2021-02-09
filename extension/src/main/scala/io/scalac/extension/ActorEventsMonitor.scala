package io.scalac.extension

import scala.collection.immutable
import scala.concurrent.duration._

import akka.actor.{ typed, ActorRef, ActorRefProvider, ActorSystem }

import org.slf4j.LoggerFactory

import io.scalac.extension.config.CachingConfig
import io.scalac.extension.metric.{ ActorMetricMonitor, Asynchronized, CachingMonitor }
import io.scalac.extension.metric.ActorMetricMonitor.Labels
import io.scalac.extension.model.Node

class ActorEventsMonitor
object ActorEventsMonitor {

  private val logger = LoggerFactory.getLogger(getClass)

  def start(
    actorMonitor: ActorMetricMonitor with Asynchronized,
    actorSystem: typed.ActorSystem[_],
    node: Option[Node],
    actorTreeRunner: ActorTreeRunner = ReflectiveActorTreeRunner,
    actorMetricsReader: ActorMetricsReader = ReflectiveActorMetricsReader
  ): Unit = {

    import actorSystem.{ classicSystem, settings }

    val monitor = CachingMonitor(actorMonitor, CachingConfig.fromConfig(settings.config, "actor"))

    def runActorTree(root: ActorRef): Unit = {
      report(root)
      actorTreeRunner.getChildren(root).foreach(runActorTree)
    }

    def report(actor: ActorRef): Unit = {
      logger.trace(s"Reporting metrics of actor $actor")
      val bound = monitor.bind(Labels(actor.path.toStringWithoutAddress, node))

      // mailbox
      actorMetricsReader.mailboxSize(actor).foreach { mailboxSize =>
        bound.mailboxSize.setValue(mailboxSize)
        logger.trace(s"Recorded mailbox size for actor $actor: $mailboxSize")
      }

      // ...
    }

    actorMonitor.onCollect {
      runActorTree(actorTreeRunner.getRootGuardian(classicSystem))
    }

  }

  trait ActorTreeRunner {
    def getChildren(actor: ActorRef): Iterable[ActorRef]
    def getRootGuardian(system: ActorSystem): ActorRef
  }

  object ReflectiveActorTreeRunner extends ActorTreeRunner {
    import ReflectiveActorMonitorsUtils._

    private val getSystemProviderReflectively = {
      classOf[ActorSystem]
        .getMethod("provider")
    }

    private val getRootGuardianReflectively = {
      classOf[ActorRefProvider]
        .getMethod("rootGuardian")
    }

    private val getChildrenReflectively = {
      actorRefWithCellClass
        .getMethod("children")
    }

    def getChildren(actor: ActorRef): immutable.Iterable[ActorRef] =
      if (isLocalActorRefWithCell(actor)) {
        getChildrenReflectively.invoke(actor).asInstanceOf[immutable.Iterable[ActorRef]]
      } else {
        immutable.Iterable.empty
      }

    def getRootGuardian(system: ActorSystem): ActorRef = {
      val provider = getSystemProviderReflectively.invoke(system)
      getRootGuardianReflectively.invoke(provider).asInstanceOf[ActorRef]
    }
  }

  trait ActorMetricsReader {
    def mailboxSize(actor: ActorRef): Option[Int]
  }

  object ReflectiveActorMetricsReader extends ActorMetricsReader {
    import ReflectiveActorMonitorsUtils._

    private val getNumberOfMessagesReflectively = cellClass.getDeclaredMethod("numberOfMessages")

    def mailboxSize(actor: ActorRef): Option[Int] =
      if (isLocalActorRefWithCell(actor)) {
        val cell = underlyingReflectively.invoke(actor)
        Some(getNumberOfMessagesReflectively.invoke(cell).asInstanceOf[Int])
      } else None

  }

  private object ReflectiveActorMonitorsUtils {
    val actorRefWithCellClass       = Class.forName("akka.actor.ActorRefWithCell")
    val cellClass                   = Class.forName("akka.actor.Cell")
    val underlyingReflectively      = actorRefWithCellClass.getDeclaredMethod("underlying")
    private val isLocalReflectively = cellClass.getDeclaredMethod("isLocal")
    def isLocalActorRefWithCell(actorRef: ActorRef): Boolean =
      actorRefWithCellClass.isInstance(actorRef) &&
        isLocalReflectively.invoke(underlyingReflectively.invoke(actorRef)).asInstanceOf[Boolean]
  }

}
