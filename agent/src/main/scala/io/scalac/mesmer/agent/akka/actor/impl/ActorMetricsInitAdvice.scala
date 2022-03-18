package io.scalac.mesmer.agent.akka.actor.impl

import akka.actor.ActorContext
import com.typesafe.config.ConfigFactory
import io.opentelemetry.api.common.Attributes
import net.bytebuddy.asm.Advice
import net.bytebuddy.asm.Advice.OnMethodEnter

import io.scalac.mesmer.agent.akka.actor.AkkaActorAgent
import io.scalac.mesmer.core.actor.ActorCellDecorator
import io.scalac.mesmer.core.actor.ActorCellMetrics

object ActorMetricsInitAdvice {

  @OnMethodEnter
  def initializeMetrics(@Advice.This cell: ActorContext): Unit = {

    val metrics = new ActorCellMetrics()
    val config  = AkkaActorAgent.module.enabled(ConfigFactory.load())

    if (config.receivedMessages) metrics.initReceivedMessages()
    if (config.processedMessages) metrics.initUnhandledMessages()
    if (config.sentMessages) metrics.initSentMessages()

    if (config.mailboxTimeCount || config.mailboxTimeSum || config.mailboxTimeMax || config.mailboxTimeMin) {
      metrics.initMailboxTimeAgg()
    }

    if (config.failedMessages) {
      metrics.initFailedMessages()
      metrics.initExceptionHandledMarker()
    }

    if (
      config.processingTimeMin
      || config.processingTimeMax
      || config.processingTimeSum
      || config.processingTimeCount
    ) {
      metrics.initProcessingTimeAgg()
      metrics.initProcessingTimer()
    }

    ActorCellDecorator.set(cell, metrics)

    val attributes =
      Attributes
        .builder()
        .put("actor_path", cell.self.path.toString)
        .build()

    ActorCellDecorator.setAttributes(cell, attributes)

  }
}
