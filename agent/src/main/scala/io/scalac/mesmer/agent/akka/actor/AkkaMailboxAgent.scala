package io.scalac.mesmer.agent.akka.actor

import java.util.concurrent.BlockingQueue
import java.util.concurrent.Callable
import java.util.concurrent.LinkedBlockingQueue

import akka.AkkaMirror.ActorRefWithCell
import akka.AkkaMirror.Cell
import akka.dispatch._
import akka.util.BoundedBlockingQueue
import akka.{ actor => classic }
import net.bytebuddy.asm.Advice
import net.bytebuddy.description.`type`.TypeDescription
import net.bytebuddy.implementation.FieldAccessor
import net.bytebuddy.implementation.MethodDelegation
import net.bytebuddy.implementation.bind.annotation.SuperCall
import net.bytebuddy.implementation.bind.annotation.This
import net.bytebuddy.matcher.ElementMatchers

import scala.reflect.ClassTag
import scala.reflect.classTag

import io.scalac.mesmer.agent.Agent
import io.scalac.mesmer.agent.util.i13n.InstrumentModuleFactory
import io.scalac.mesmer.agent.util.i13n._
import io.scalac.mesmer.core.model.SupportedModules
import io.scalac.mesmer.core.support.ModulesSupport
import io.scalac.mesmer.core.util.ReflectionFieldUtils
import io.scalac.mesmer.extension.actor.ActorCellDecorator

class BoundedNodeMessageQueueAdvice
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
  val lastResultFieldName = "_lastEnqueueResult"

  lazy val (lastResultGetter, lastResultSetter) =
    ReflectionFieldUtils.getHandlers(classTag[T].runtimeClass, lastResultFieldName)

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

object AkkaMailboxAgent extends InstrumentModuleFactory {
  protected val supportedModules: SupportedModules =
    SupportedModules(ModulesSupport.akkaActorModule, ModulesSupport.akkaActor)

  private val boundedQueueBasesMailbox: TypeInstrumentation =
    instrument(
      "akka.dispatch.AbstractBoundedNodeQueue"
    )
      .defineField[Boolean](LastEnqueueResult.lastResultFieldName)
      .intercept(
        "add",
        MethodDelegation.to(new AbstractBoundedNodeQueueAdvice(AbstractBoundedQueueDecorator))
      )
  private val boundedQueueBasedMailboxes: TypeInstrumentation = instrument(
    `type`(
      "akka.dispatch.BoundedQueueBasedMessageQueue",
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

  val boundedMessageQueueSemantics: TypeInstrumentation = instrument(
    hierarchy("akka.dispatch.BoundedMessageQueueSemantics")
      .and(ElementMatchers.not[TypeDescription](ElementMatchers.isAbstract[TypeDescription]))
  )
    .visit[BoundedNodeMessageQueueAdvice]("enqueue")

  def agent: Agent =
    Agent(boundedQueueBasesMailbox, boundedQueueBasedMailboxes, boundedMessageQueueSemantics)

}
