package io.scalac.mesmer.otelextension.instrumentations.akka.actor

import java.lang.{ Boolean => JBoolean }

import akka.MesmerMirrorTypes.ActorRefWithCell
import akka.MesmerMirrorTypes.Cell
import akka.actor.BoundedQueueProxy
import akka.actor.ProxiedQueue
import akka.dispatch._
import akka.{ actor => classic }
import io.opentelemetry.instrumentation.api.field.VirtualField
import net.bytebuddy.asm.Advice
import net.bytebuddy.asm.Advice.OnMethodExit
import net.bytebuddy.asm.Advice.Return
import net.bytebuddy.description.`type`.TypeDescription
import net.bytebuddy.matcher.ElementMatchers

import io.scalac.mesmer.agent.Agent
import io.scalac.mesmer.agent.AgentInstrumentation
import io.scalac.mesmer.agent.util.i13n._
import io.scalac.mesmer.core.actor.ActorCellDecorator
import io.scalac.mesmer.instrumentation.actor.impl.BoundedQueueBasedMessageQueueAdvice

object BoundedNodeMessageQueueAdvice {

  @Advice.OnMethodExit
  def handleDroppedMessages(
    @Advice.Argument(0) ref: classic.ActorRef,
    @Advice.This self: BoundedMessageQueueSemantics
  ): Unit = {
    val withCell = ref.asInstanceOf[ActorRefWithCell]
    self match {
      case _: BoundedNodeMessageQueue =>
        incDropped(
          VirtualField
            .find(classOf[AbstractBoundedNodeQueue[_]], classOf[JBoolean])
            .get(self.asInstanceOf[AbstractBoundedNodeQueue[_]]),
          withCell.underlying
        )

      case bm: BoundedQueueBasedMessageQueue =>
        val result = bm.queue.asInstanceOf[BoundedQueueProxy[_]].getResult
        incDropped(result, withCell.underlying)
      case _ =>
    }
  }

  @inline
  private def incDropped(result: Boolean, cell: Cell): Unit =
    if (!result && (cell ne null)) {
      val maybeActorMetrics = ActorCellDecorator.getMetrics(cell)
      for {
        actorMetrics <- maybeActorMetrics if actorMetrics.droppedMessages.isDefined
      } actorMetrics.droppedMessages.get.inc()
    }
}

object AbstractBoundedNodeQueueAdvice {

  @OnMethodExit
  def add(@Return result: Boolean, @Advice.This self: Object): Unit = {
    val field: VirtualField[AbstractBoundedNodeQueue[_], JBoolean] = VirtualField
      .find(classOf[AbstractBoundedNodeQueue[_]], classOf[JBoolean])

    field.set(self.asInstanceOf[AbstractBoundedNodeQueue[_]], result)
  }

}

private[actor] trait AkkaMailboxInstrumentations {
  this: InstrumentModuleFactory[_] =>

  /**
   * Instrumentation that add boolean field that will hold if last enqueue was successful or not
   */
  private val boundedQueueBasesMailbox: AgentInstrumentation =
    instrument(
      "akka.dispatch.AbstractBoundedNodeQueue".fqcn
    ).visit(AbstractBoundedNodeQueueAdvice, "add")

  /**
   * Instrumentation that add proxy to mailbox queue
   */
  private val boundedQueueBasedMailboxes: AgentInstrumentation = instrument(
    `type`(
      "akka.dispatch.BoundedQueueBasedMessageQueue".fqcn,
      ElementMatchers
        .hasSuperType[TypeDescription](
          ElementMatchers.named[TypeDescription]("akka.dispatch.BoundedQueueBasedMessageQueue")
        )
        .and(
          ElementMatchers
            .hasSuperType[TypeDescription](
              ElementMatchers.named[TypeDescription]("java.util.concurrent.BlockingQueue")
            )
        )
        .and(ElementMatchers.not[TypeDescription](ElementMatchers.isAbstract[TypeDescription]))
    )
  )
    .visit[ProxiedQueue](constructor)
    .visit[BoundedQueueBasedMessageQueueAdvice]("queue")

  /**
   * Instrumentation that increase dropped messages if enqueue was a failure and bounded queue is in use
   */
  private val boundedMessageQueueSemantics: AgentInstrumentation = instrument(
    hierarchy("akka.dispatch.BoundedMessageQueueSemantics".fqcn)
      .and(ElementMatchers.not[TypeDescription](ElementMatchers.isAbstract[TypeDescription]))
  )
    .visit(BoundedNodeMessageQueueAdvice, "enqueue")

  protected val boundedQueueAgent: Agent =
    Agent(boundedQueueBasesMailbox, boundedQueueBasedMailboxes, boundedMessageQueueSemantics)

}
