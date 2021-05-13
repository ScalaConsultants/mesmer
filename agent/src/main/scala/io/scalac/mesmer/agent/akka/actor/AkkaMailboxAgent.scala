package io.scalac.mesmer.agent.akka.actor

import akka.AkkaMirror.ActorRefWithCell
import akka.dispatch.{ AbstractBoundedNodeQueue, BoundedNodeMessageQueue }
import akka.{ actor => classic }
import io.scalac.mesmer.agent.Agent
import io.scalac.mesmer.agent.util.i13n.{ InstrumentModuleFactory, _ }
import io.scalac.mesmer.core.model.SupportedModules
import io.scalac.mesmer.core.support.ModulesSupport
import io.scalac.mesmer.core.util.ReflectionFieldUtils
import io.scalac.mesmer.extension.actor.ActorCellDecorator
import net.bytebuddy.asm.Advice._
import net.bytebuddy.matcher.ElementMatchers
import net.bytebuddy.pool.TypePool

import scala.reflect.{ classTag, ClassTag }

class BoundedNodeMessageQueueAdvice
object BoundedNodeMessageQueueAdvice {

  @OnMethodExit
  def checkSuccess(@Argument(0) ref: classic.ActorRef, @This queue: Object): Unit = {
    val withCell = ref.asInstanceOf[ActorRefWithCell]
    queue match {
      case _: BoundedNodeMessageQueue =>
        if (!AbstractBoundedQueueDecorator.getResult(queue) && (withCell.underlying ne null)) {
          for {
            actorMetrics <- ActorCellDecorator.get(withCell.underlying)
            dropped      <- actorMetrics.droppedMessages
          } dropped.inc()
        }

    }
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
//{
//
//  val lastResultFieldName = "_lastEnqueueResult"
//
//  private lazy val (lastResultGetter, lastResultSetter) = {
//    //    val clazz = classTag[T].runtimeClass
//    val clazz = classOf[AbstractBoundedNodeQueue[_]]
//    println(s"handler for class ${clazz}")
//
//    ReflectionFieldUtils.getHandlers(clazz, lastResultFieldName)
//  }
//
//  def setResult(queue: Object, result: Boolean): Unit = lastResultSetter.invoke(queue, result)
//
//  def getResult(queue: Object): Boolean = lastResultGetter.invoke(queue)
//}

class AbstractBoundedNodeQueueAdvice
object AbstractBoundedNodeQueueAdvice {

  @OnMethodExit
  def add(@Return result: Boolean, @This self: Object): Unit =
    AbstractBoundedQueueDecorator.setResult(self, result)

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
      .visit[AbstractBoundedNodeQueueAdvice]("add")
  }

  val boundedMessageQueueSemantics = instrument(hierarchy("akka.dispatch.BoundedMessageQueueSemantics"))
    .visit[BoundedNodeMessageQueueAdvice]("enqueue")

  
  def agent(typePool: TypePool) = Agent(boundedQueueBasesMailbox(typePool), boundedMessageQueueSemantics)

}
