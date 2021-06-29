//package io.scalac.mesmer.agent.akka.actor.impl
//
//import akka.dispatch.MailboxType
//import io.scalac.mesmer.core.actor.{ActorCellDecorator, ActorCellMetrics}
//import net.bytebuddy.asm.Advice.{Argument, FieldValue, OnMethodExit, This}
//
//object ActorCellConstructorInstrumentation {
//
////  @OnMethodExit
////  def onEnter(@This actorCell: Object, @Argument(1) mailboxType: MailboxType): Unit = {
////
////  }
//
//  @OnMethodExit
//  def setUpCellMetrics(@FieldValue(ActorCellDecorator.fieldName) cellMetrics: ActorCellMetrics)
////    ActorCellDecorator.initialize(actorCell, mailboxType)
//}
