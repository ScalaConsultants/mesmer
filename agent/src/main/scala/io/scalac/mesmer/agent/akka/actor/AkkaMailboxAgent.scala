package io.scalac.mesmer.agent.akka.actor

import akka.AkkaMirror.ActorRefWithCell
import akka.dispatch._
import akka.util.BoundedBlockingQueue
import akka.{ actor => classic }
import io.scalac.mesmer.agent.Agent
import io.scalac.mesmer.agent.util.i13n.{ InstrumentModuleFactory, _ }
import io.scalac.mesmer.core.model.SupportedModules
import io.scalac.mesmer.core.support.ModulesSupport
import io.scalac.mesmer.core.util.ReflectionFieldUtils
import io.scalac.mesmer.extension.actor.ActorCellDecorator
import net.bytebuddy.asm.Advice
import net.bytebuddy.description.`type`.TypeDescription
import net.bytebuddy.implementation.bind.annotation.{ Super, SuperCall, This }
import net.bytebuddy.implementation.{ FieldAccessor, MethodDelegation }
import net.bytebuddy.matcher.ElementMatchers
import net.bytebuddy.pool.TypePool

import java.util.concurrent.{ BlockingQueue, Callable, LinkedBlockingQueue }
import scala.reflect.{ classTag, ClassTag }

class BoundedNodeMessageQueueAdvice
object BoundedNodeMessageQueueAdvice {

  @Advice.OnMethodExit
  def checkSuccess(@Advice.Argument(0) ref: classic.ActorRef, @Advice.This self: Object): Unit = {
    val withCell = ref.asInstanceOf[ActorRefWithCell]
    self match {
      case _: BoundedNodeMessageQueue =>
        if (!AbstractBoundedQueueDecorator.getResult(self) && (withCell.underlying ne null)) {
          for {
            actorMetrics <- ActorCellDecorator.get(withCell.underlying)
            dropped      <- actorMetrics.droppedMessages
          } dropped.inc()
        }

      case bm: BoundedQueueBasedMessageQueue =>
        val result = bm.queue.asInstanceOf[BoundedQueueProxy[_]].getResult()
        if (result && (withCell.underlying ne null)) {
          for {
            actorMetrics <- ActorCellDecorator.get(withCell.underlying)
            dropped      <- actorMetrics.droppedMessages
          } dropped.inc()
        }
    }
  }
}

object QueueBasedMailboxConstructor {

  @Advice.OnMethodEnter
  def constructor(@Super elo: Object): Unit = {}

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
  protected def supportedModules: SupportedModules =
    SupportedModules(ModulesSupport.akkaActorModule, ModulesSupport.akkaActor)

  private def boundedQueueBasesMailbox(typePool: TypePool): TypeInstrumentation = {
    val abstractBoundedNodeQueueType = typePool
      .describe("akka.dispatch.AbstractBoundedNodeQueue")
      .resolve()
    val boundedNodeMessageQueue = typePool
      .describe("akka.dispatch.BoundedNodeMessageQueue")
      .resolve()

    instrument(
      `type`(
        "akka.dispatch.BoundedNodeMessageQueue",
        ElementMatchers.is(abstractBoundedNodeQueueType).and(ElementMatchers.isSuperTypeOf(boundedNodeMessageQueue))
      )
    )
      .defineField[Boolean](LastEnqueueResult.lastResultFieldName)
      .intercept(
        "add",
        MethodDelegation.to(new AbstractBoundedNodeQueueAdvice(AbstractBoundedQueueDecorator))
      )
  }
  private val boundedMailbox: TypeInstrumentation = instrument(
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

  val boundedMessageQueueSemantics = instrument(hierarchy("akka.dispatch.BoundedMessageQueueSemantics"))
    .visit[BoundedNodeMessageQueueAdvice]("enqueue")

  def agent(typePool: TypePool) =
    Agent(boundedQueueBasesMailbox(typePool), boundedMailbox, boundedMessageQueueSemantics)

}
