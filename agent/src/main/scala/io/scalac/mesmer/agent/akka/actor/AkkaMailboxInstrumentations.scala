package io.scalac.mesmer.agent.akka.actor

import akka.AkkaMirror.{ActorRefWithCell, Cell}
import akka.dispatch._
import akka.util.BoundedBlockingQueue
import akka.{actor => classic}
import io.scalac.mesmer.agent.util.i13n._
import io.scalac.mesmer.agent.{Agent, AgentInstrumentation}
import io.scalac.mesmer.core.util.ReflectionFieldUtils
import io.scalac.mesmer.extension.actor.ActorCellDecorator
import net.bytebuddy.asm.Advice
import net.bytebuddy.description.`type`.TypeDescription
import net.bytebuddy.implementation.bind.annotation.{SuperCall, This}
import net.bytebuddy.implementation.{FieldAccessor, MethodDelegation}
import net.bytebuddy.matcher.ElementMatchers

import java.util.concurrent.{BlockingQueue, Callable, LinkedBlockingQueue}
import scala.reflect.{ClassTag, classTag}

object BoundedNodeMessageQueueAdvice {

  @Advice.OnMethodExit
  def handleDroppedMessages(
    @Advice.Argument(0) ref: classic.ActorRef,
    @Advice.This self: BoundedMessageQueueSemantics
  ): Unit = {
    val withCell = ref.asInstanceOf[ActorRefWithCell]
    self match {
      case _: BoundedNodeMessageQueue =>
        incDropped(AbstractBoundedQueueDecorator.getResult(self), withCell.underlying)

      case bm: BoundedQueueBasedMessageQueue =>
        val result = bm.queue.asInstanceOf[BoundedQueueProxy[_]].getResult
        incDropped(result, withCell.underlying)
      case _ =>
    }
  }

  @inline
  private def incDropped(result: Boolean, cell: Cell): Unit =
    if (result && (cell ne null)) {
      for {
        actorMetrics <- ActorCellDecorator.get(cell)
        dropped      <- actorMetrics.droppedMessages
      } dropped.inc()
    }
}

object LastEnqueueResult {
  final val lastResultFieldName = "_lastEnqueueResult"
}

abstract class LastEnqueueResult[T: ClassTag] {

  lazy val (lastResultGetter, lastResultSetter) =
    ReflectionFieldUtils.getHandlers(classTag[T].runtimeClass, LastEnqueueResult.lastResultFieldName)

  def setResult(queue: Object, result: Boolean): Unit = lastResultSetter.invoke(queue, result)

  def getResult(queue: Object): Boolean = lastResultGetter.invoke(queue)
}

object AbstractBoundedQueueDecorator extends LastEnqueueResult[AbstractBoundedNodeQueue[_]]
object LinkedBlockingQueueDecorator  extends LastEnqueueResult[LinkedBlockingQueue[_]]
object BoundedBlockingQueueDecorator extends LastEnqueueResult[BoundedBlockingQueue[_]]

class AbstractBoundedNodeQueueAdvice(lastEnqueueResult: => LastEnqueueResult[_]) {

  private lazy val ler = lastEnqueueResult

  def add(@This self: Object, @SuperCall impl: Callable[Boolean]): Boolean = {
    val result = impl.call()
    ler.setResult(self, result)
    result
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
    )
      .defineField[Boolean](LastEnqueueResult.lastResultFieldName)
      .intercept(
        "add",
        MethodDelegation.to(new AbstractBoundedNodeQueueAdvice(AbstractBoundedQueueDecorator))
      )

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
    .defineField[BlockingQueue[_]](ProxiedQueue.queueFieldName)
    .visit[ProxiedQueue](constructor)
    .intercept(ElementMatchers.named("queue"), FieldAccessor.ofField(ProxiedQueue.queueFieldName))

  /**
   * Instrumentation that increase dropped messages if enqueue was a failure and bounded queue is in use
   */
  private val boundedMessageQueueSemantics: AgentInstrumentation = instrument(
    hierarchy("akka.dispatch.BoundedMessageQueueSemantics".fqcn)
      .and(ElementMatchers.not[TypeDescription](ElementMatchers.isAbstract[TypeDescription]))
  )
    .visit(BoundedNodeMessageQueueAdvice, "enqueue")

  protected val boundedQueueAgent = Agent(boundedQueueBasesMailbox, boundedQueueBasedMailboxes, boundedMessageQueueSemantics)

}
