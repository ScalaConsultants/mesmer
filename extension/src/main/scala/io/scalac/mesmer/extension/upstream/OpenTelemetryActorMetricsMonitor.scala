package io.scalac.mesmer.extension.upstream

import com.typesafe.config.Config
import io.opentelemetry.api.metrics.Meter

import io.scalac.mesmer.core.config.Configuration
import io.scalac.mesmer.core.config.MesmerConfiguration
import io.scalac.mesmer.core.module.AkkaActorModule
import io.scalac.mesmer.extension.metric.ActorMetricsMonitor
import io.scalac.mesmer.extension.metric.MetricObserver
import io.scalac.mesmer.extension.metric.RegisterRoot
import io.scalac.mesmer.extension.upstream.OpenTelemetryActorMetricsMonitor.MetricNames
import io.scalac.mesmer.extension.upstream.opentelemetry._

object OpenTelemetryActorMetricsMonitor {

  final case class MetricNames(
    mailboxSize: String,
    mailboxTimeMin: String,
    mailboxTimeMax: String,
    mailboxTimeSum: String,
    mailboxTimeCount: String,
    stashedMessages: String,
    receivedMessages: String,
    processedMessages: String,
    failedMessages: String,
    processingTimeMin: String,
    processingTimeMax: String,
    processingTimeSum: String,
    processingTimeCount: String,
    sentMessages: String,
    droppedMessages: String
  )

  object MetricNames extends MesmerConfiguration[MetricNames] with Configuration {

    protected val mesmerConfig: String = "mesmer.metrics.actor-metrics"

    protected def extractFromConfig(config: Config): MetricNames = MetricNames(
      mailboxSize = config
        .tryValue("mailbox-size")(_.getString)
        .getOrElse(defaultConfig.mailboxSize),
      mailboxTimeMin = config
        .tryValue("mailbox-time-min")(_.getString)
        .getOrElse(defaultConfig.mailboxTimeMin),
      mailboxTimeMax = config
        .tryValue("mailbox-time-max")(_.getString)
        .getOrElse(defaultConfig.mailboxTimeMax),
      mailboxTimeSum = config
        .tryValue("mailbox-time-sum")(_.getString)
        .getOrElse(defaultConfig.mailboxTimeSum),
      mailboxTimeCount = config
        .tryValue("mailbox-time-count")(_.getString)
        .getOrElse(defaultConfig.mailboxTimeCount),
      stashedMessages = config
        .tryValue("stash-size")(_.getString)
        .getOrElse(defaultConfig.stashedMessages),
      receivedMessages = config
        .tryValue("received-messages")(_.getString)
        .getOrElse(defaultConfig.receivedMessages),
      processedMessages = config
        .tryValue("processed-messages")(_.getString)
        .getOrElse(defaultConfig.processedMessages),
      failedMessages = config
        .tryValue("failed-messages")(_.getString)
        .getOrElse(defaultConfig.failedMessages),
      processingTimeMin = config
        .tryValue("processing-time-min")(_.getString)
        .getOrElse(defaultConfig.processingTimeMin),
      processingTimeMax = config
        .tryValue("processing-time-max")(_.getString)
        .getOrElse(defaultConfig.processingTimeMax),
      processingTimeSum = config
        .tryValue("processing-time-sum")(_.getString)
        .getOrElse(defaultConfig.processingTimeSum),
      processingTimeCount = config
        .tryValue("processing-time-count")(_.getString)
        .getOrElse(defaultConfig.processingTimeCount),
      sentMessages = config
        .tryValue("sent-messages")(_.getString)
        .getOrElse(defaultConfig.sentMessages),
      droppedMessages = config
        .tryValue("dropped-messages")(_.getString)
        .getOrElse(defaultConfig.droppedMessages)
    )

    val defaultConfig: MetricNames =
      MetricNames(
        mailboxSize = "akka_actor_mailbox_size",
        mailboxTimeMin = "akka_actor_mailbox_time_min",
        mailboxTimeMax = "akka_actor_mailbox_time_max",
        mailboxTimeSum = "akka_actor_mailbox_time_sum",
        mailboxTimeCount = "akka_actor_mailbox_time_count",
        stashedMessages = "akka_actor_stashed_total",
        receivedMessages = "akka_actor_received_messages_total",
        processedMessages = "akka_actor_processed_messages_total",
        failedMessages = "akka_actor_failed_messages",
        processingTimeMin = "akka_actor_processing_time_min",
        processingTimeMax = "akka_actor_processing_time_max",
        processingTimeSum = "akka_actor_processing_time_sum",
        processingTimeCount = "akka_actor_processing_time_count",
        sentMessages = "akka_actor_sent_messages_total",
        droppedMessages = "akka_actor_dropped_messages_total"
      )

  }

  def apply(
    meter: Meter,
    moduleConfig: AkkaActorModule.All[Boolean],
    config: Config
  ): OpenTelemetryActorMetricsMonitor =
    new OpenTelemetryActorMetricsMonitor(meter, moduleConfig, MetricNames.fromConfig(config))

}

final class OpenTelemetryActorMetricsMonitor(
  meter: Meter,
  moduleConfig: AkkaActorModule.All[Boolean],
  metricNames: MetricNames
) extends ActorMetricsMonitor {

  private lazy val mailboxSizeObserver = new GaugeBuilderAdapter[ActorMetricsMonitor.Attributes](
    meter
      .gaugeBuilder(metricNames.mailboxSize)
      .ofLongs()
      .setDescription("Tracks the size of an Actor's mailbox")
  )

  private lazy val mailboxTimeCountObserver = new GaugeBuilderAdapter[ActorMetricsMonitor.Attributes](
    meter
      .gaugeBuilder(metricNames.mailboxTimeCount)
      .ofLongs()
      .setDescription("Tracks the count of messages in an Actor's mailbox")
  )

  private lazy val mailboxTimeMinObserver = new GaugeBuilderAdapter[ActorMetricsMonitor.Attributes](
    meter
      .gaugeBuilder(metricNames.mailboxTimeMin)
      .ofLongs()
      .setDescription("Tracks the minimum time of an message in an Actor's mailbox")
  )

  private lazy val mailboxTimeMaxObserver = new GaugeBuilderAdapter[ActorMetricsMonitor.Attributes](
    meter
      .gaugeBuilder(metricNames.mailboxTimeMax)
      .ofLongs()
      .setDescription("Tracks the maximum time of an message in an Actor's mailbox")
  )

  private lazy val mailboxTimeSumObserver = new GaugeBuilderAdapter[ActorMetricsMonitor.Attributes](
    meter
      .gaugeBuilder(metricNames.mailboxTimeSum)
      .ofLongs()
      .setDescription("Tracks the sum time of the messages in an Actor's mailbox")
  )

  private lazy val stashSizeCounter = new LongSumObserverBuilderAdapter[ActorMetricsMonitor.Attributes](
    meter
      .counterBuilder(metricNames.stashedMessages)
      .setDescription("Tracks stash operations on actors")
  )

  private lazy val receivedMessagesSumObserver = new LongSumObserverBuilderAdapter[ActorMetricsMonitor.Attributes](
    meter
      .counterBuilder(metricNames.receivedMessages)
      .setDescription("Tracks the sum of received messages in an Actor")
  )

  private lazy val processedMessagesSumObserver = new LongSumObserverBuilderAdapter[ActorMetricsMonitor.Attributes](
    meter
      .counterBuilder(metricNames.processedMessages)
      .setDescription("Tracks the sum of processed messages in an Actor")
  )

  private lazy val failedMessagesSumObserver = new LongSumObserverBuilderAdapter[ActorMetricsMonitor.Attributes](
    meter
      .counterBuilder(metricNames.failedMessages)
      .setDescription("Tracks the sum of failed messages in an Actor")
  )

  private lazy val processingTimeCountObserver = new GaugeBuilderAdapter[ActorMetricsMonitor.Attributes](
    meter
      .gaugeBuilder(metricNames.processingTimeCount)
      .ofLongs()
      .setDescription("Tracks the amount of processed messages")
  )

  private lazy val processingTimeMinObserver = new GaugeBuilderAdapter[ActorMetricsMonitor.Attributes](
    meter
      .gaugeBuilder(metricNames.processingTimeMin)
      .ofLongs()
      .setDescription("Tracks the minimum processing time of an message in an Actor's receive handler")
  )

  private lazy val processingTimeMaxObserver = new GaugeBuilderAdapter[ActorMetricsMonitor.Attributes](
    meter
      .gaugeBuilder(metricNames.processingTimeMax)
      .ofLongs()
      .setDescription("Tracks the maximum processing time of an message in an Actor's receive handler")
  )

  private lazy val processingTimeSumObserver = new GaugeBuilderAdapter[ActorMetricsMonitor.Attributes](
    meter
      .gaugeBuilder(metricNames.processingTimeSum)
      .ofLongs()
      .setDescription("Tracks the sum processing time of an message in an Actor's receive handler")
  )

  private lazy val sentMessagesObserver = new LongSumObserverBuilderAdapter[ActorMetricsMonitor.Attributes](
    meter
      .counterBuilder(metricNames.sentMessages)
      .setDescription("Tracks the sum of sent messages in an Actor")
  )

  private lazy val droppedMessagesObserver = new LongSumObserverBuilderAdapter[ActorMetricsMonitor.Attributes](
    meter
      .counterBuilder(metricNames.droppedMessages)
      .setDescription("Tracks the sum of dropped messages in an Actor")
  )

  def bind(): OpenTelemetryBoundMonitor =
    new OpenTelemetryBoundMonitor

  class OpenTelemetryBoundMonitor
      extends ActorMetricsMonitor.BoundMonitor
      with RegisterRoot
      with SynchronousInstrumentFactory {

    val mailboxSize: MetricObserver[Long, ActorMetricsMonitor.Attributes] =
      if (moduleConfig.mailboxSize) mailboxSizeObserver.createObserver(this) else MetricObserver.noop

    val mailboxTimeCount: MetricObserver[Long, ActorMetricsMonitor.Attributes] =
      if (moduleConfig.mailboxTimeCount) mailboxTimeCountObserver.createObserver(this) else MetricObserver.noop

    val mailboxTimeMin: MetricObserver[Long, ActorMetricsMonitor.Attributes] =
      if (moduleConfig.mailboxTimeMin) mailboxTimeMinObserver.createObserver(this) else MetricObserver.noop

    val mailboxTimeMax: MetricObserver[Long, ActorMetricsMonitor.Attributes] =
      if (moduleConfig.mailboxTimeMax) mailboxTimeMaxObserver.createObserver(this) else MetricObserver.noop

    val mailboxTimeSum: MetricObserver[Long, ActorMetricsMonitor.Attributes] =
      if (moduleConfig.mailboxTimeSum) mailboxTimeSumObserver.createObserver(this) else MetricObserver.noop

    val stashedMessages: MetricObserver[Long, ActorMetricsMonitor.Attributes] =
      if (moduleConfig.stashedMessages) stashSizeCounter.createObserver(this) else MetricObserver.noop

    val receivedMessages: MetricObserver[Long, ActorMetricsMonitor.Attributes] =
      if (moduleConfig.receivedMessages) receivedMessagesSumObserver.createObserver(this) else MetricObserver.noop

    val processedMessages: MetricObserver[Long, ActorMetricsMonitor.Attributes] =
      if (moduleConfig.processedMessages) processedMessagesSumObserver.createObserver(this) else MetricObserver.noop

    val failedMessages: MetricObserver[Long, ActorMetricsMonitor.Attributes] =
      if (moduleConfig.failedMessages) failedMessagesSumObserver.createObserver(this) else MetricObserver.noop

    val processingTimeCount: MetricObserver[Long, ActorMetricsMonitor.Attributes] =
      if (moduleConfig.processingTimeCount) processingTimeCountObserver.createObserver(this) else MetricObserver.noop

    val processingTimeMin: MetricObserver[Long, ActorMetricsMonitor.Attributes] =
      if (moduleConfig.processingTimeMin) processingTimeMinObserver.createObserver(this) else MetricObserver.noop

    val processingTimeMax: MetricObserver[Long, ActorMetricsMonitor.Attributes] =
      if (moduleConfig.processingTimeMax) processingTimeMaxObserver.createObserver(this) else MetricObserver.noop

    val processingTimeSum: MetricObserver[Long, ActorMetricsMonitor.Attributes] =
      if (moduleConfig.processingTimeSum) processingTimeSumObserver.createObserver(this) else MetricObserver.noop

    val sentMessages: MetricObserver[Long, ActorMetricsMonitor.Attributes] =
      if (moduleConfig.sentMessages) sentMessagesObserver.createObserver(this) else MetricObserver.noop

    val droppedMessages: MetricObserver[Long, ActorMetricsMonitor.Attributes] =
      if (moduleConfig.droppedMessages)
        droppedMessagesObserver
          .createObserver(this)
      else MetricObserver.noop
  }

}
