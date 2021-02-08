package io.scalac.extension

import scala.collection.immutable
import scala.concurrent.duration._

import akka.actor.{ typed, ActorRef, ActorSystem }

import io.scalac.extension.config.CachingConfig
import io.scalac.extension.metric.{ ActorMetricMonitor, CachingMonitor }
import io.scalac.extension.metric.ActorMetricMonitor.Labels
import io.scalac.extension.model.Node

class ActorEventsMonitor
object ActorEventsMonitor {

  def start(
    actorMonitor: ActorMetricMonitor,
    actorSystem: typed.ActorSystem[_],
    node: Option[Node],
    pingOffset: FiniteDuration,
    actorTreeRunner: ActorTreeRunner = ReflectiveActorTreeRunner,
    actorMetricsReader: ActorMetricsReader = ReflectiveActorMetricsReader
  ): Unit = {

    import actorSystem.{ classicSystem, executionContext, log, scheduler, settings }

    val monitor = CachingMonitor(actorMonitor, CachingConfig.fromConfig(settings.config, "actor"))

    def runActorTree(root: ActorRef): Unit = {
      report(root)
      actorTreeRunner.getChildren(root).foreach(runActorTree)
    }

    def report(actor: ActorRef): Unit = {
      log.debug(s"Reporting metrics of actor $actor")
      val bound = monitor.bind(Labels(actor.path.toStringWithoutAddress, node))

      // mailbox
      actorMetricsReader.mailboxSize(actor).foreach { mailboxSize =>
        bound.mailboxSize.setValue(mailboxSize)
        log.trace(s"Recorded mailbox size for actor $actor: $mailboxSize")
      }

      // ...
    }

    scheduler.scheduleWithFixedDelay(0.second, pingOffset) { () =>
      runActorTree(actorTreeRunner.getRootGuardian(classicSystem))
    }

  }

  trait ActorTreeRunner {
    def getChildren(actor: ActorRef): Iterable[ActorRef]
    def getRootGuardian(system: ActorSystem): ActorRef
  }

  object ReflectiveActorTreeRunner extends ActorTreeRunner {
    import ReflectiveActorMonitorsUtils._

    private val getRootGuardianReflectively = {
      classOf[ActorSystem]
        .getMethod("guardian")
    }

    private val getChildrenReflectively = {
      localActorRefClass
        .getMethod("children")
    }

    def getChildren(actor: ActorRef): immutable.Iterable[ActorRef] =
      if (isLocalRef(actor)) {
        getChildrenReflectively.invoke(actor).asInstanceOf[immutable.Iterable[ActorRef]]
      } else {
        immutable.Iterable.empty
      }

    def getRootGuardian(system: ActorSystem): ActorRef =
      getRootGuardianReflectively.invoke(system).asInstanceOf[ActorRef]
  }

  trait ActorMetricsReader {
    def mailboxSize(actor: ActorRef): Option[Int]
  }

  object ReflectiveActorMetricsReader extends ActorMetricsReader {
    import ReflectiveActorMonitorsUtils._

    private val getActorCellReflectively = {
      val actorCell = localActorRefClass.getDeclaredField("actorCell")
      actorCell.setAccessible(true)
      actorCell
    }

    private val getMailboxReflectively = {
      val mailbox = Class
        .forName("akka.actor.dungeon.Dispatch")
        .getDeclaredMethod("mailbox")
      mailbox.setAccessible(true)
      mailbox
    }

    private val getNumberOfMessagesReflectively = {
      Class
        .forName("akka.dispatch.Mailbox")
        .getDeclaredMethod("numberOfMessages")
    }

    def mailboxSize(actor: ActorRef): Option[Int] =
      if (isLocalRef(actor)) {
        val actorCell = getActorCellReflectively.get(actor)
        val mailbox   = getMailboxReflectively.invoke(actorCell)
        Some(getNumberOfMessagesReflectively.invoke(mailbox).asInstanceOf[Int])
      } else None

  }

  private object ReflectiveActorMonitorsUtils {
    val localActorRefClass: Class[_]            = Class.forName("akka.actor.LocalActorRef")
    def isLocalRef(actorRef: ActorRef): Boolean = localActorRefClass.isInstance(actorRef)
  }

}
