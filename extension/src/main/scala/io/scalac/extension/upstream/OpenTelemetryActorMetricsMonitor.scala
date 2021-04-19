package io.scalac.extension.upstream

import com.typesafe.config.Config
import io.opentelemetry.api.metrics.Meter

import io.scalac.extension.metric.ActorMetricsMonitor
import io.scalac.extension.metric.MetricObserver
import io.scalac.extension.metric.RegisterRoot
import io.scalac.extension.upstream.OpenTelemetryActorMetricsMonitor.MetricNames
import io.scalac.extension.upstream.opentelemetry._

object OpenTelemetryActorMetricsMonitor {

  case class MetricNames(
    mailboxSize: String,
    mailboxTimeAvg: String,
    mailboxTimeMin: String,
    mailboxTimeMax: String,
    mailboxTimeSum: String,
    stashedMessages: String,
    receivedMessages: String,
    processedMessages: String,
    failedMessages: String,
    processingTimeAvg: String,
    processingTimeMin: String,
    processingTimeMax: String,
    processingTimeSum: String,
    sentMessages: String
  )
  object MetricNames {

    def default: MetricNames =
      MetricNames(
        "akka_actor_mailbox_size",
        "akka_actor_mailbox_time_avg",
        "akka_actor_mailbox_time_min",
        "akka_actor_mailbox_time_max",
        "akka_actor_mailbox_time_sum",
        "akka_actor_stashed_total",
        "akka_actor_received_messages_total",
        "akka_actor_processed_messages_total",
        "akka_actor_failed_messages",
        "akka_actor_processing_time_avg",
        "akka_actor_processing_time_min",
        "akka_actor_processing_time_max",
        "akka_actor_processing_time_sum",
        "akka_actor_sent_messages_totals"
      )

    def fromConfig(config: Config): MetricNames = {
      import io.scalac.extension.config.ConfigurationUtils._
      val defaultCached = default

      config
        .tryValue("io.scalac.akka-monitoring.metrics.actor-metrics")(
          _.getConfig
        )
        .map { clusterMetricsConfig =>
          val mailboxSize = clusterMetricsConfig
            .tryValue("mailbox-size")(_.getString)
            .getOrElse(defaultCached.mailboxSize)

          val mailboxTimeAvg = clusterMetricsConfig
            .tryValue("mailbox-time-avg")(_.getString)
            .getOrElse(defaultCached.mailboxTimeAvg)

          val mailboxTimeMin = clusterMetricsConfig
            .tryValue("mailbox-time-min")(_.getString)
            .getOrElse(defaultCached.mailboxTimeMin)

          val mailboxTimeMax = clusterMetricsConfig
            .tryValue("mailbox-time-max")(_.getString)
            .getOrElse(defaultCached.mailboxTimeMax)

          val mailboxTimeSum = clusterMetricsConfig
            .tryValue("mailbox-time-sum")(_.getString)
            .getOrElse(defaultCached.mailboxTimeSum)

          val stashSize = clusterMetricsConfig
            .tryValue("stash-size")(_.getString)
            .getOrElse(defaultCached.stashedMessages)

          val receivedMessages = clusterMetricsConfig
            .tryValue("received-messages")(_.getString)
            .getOrElse(defaultCached.receivedMessages)

          val processedMessages = clusterMetricsConfig
            .tryValue("processed-messages")(_.getString)
            .getOrElse(defaultCached.processedMessages)

          val failedMessages = clusterMetricsConfig
            .tryValue("failed-messages")(_.getString)
            .getOrElse(defaultCached.failedMessages)

          val processingTimeAvg = clusterMetricsConfig
            .tryValue("processing-time-avg")(_.getString)
            .getOrElse(defaultCached.processingTimeAvg)

          val processingTimeMin = clusterMetricsConfig
            .tryValue("processing-time-min")(_.getString)
            .getOrElse(defaultCached.processingTimeMin)

          val processingTimeMax = clusterMetricsConfig
            .tryValue("processing-time-max")(_.getString)
            .getOrElse(defaultCached.processingTimeMax)

          val processingTimeSum = clusterMetricsConfig
            .tryValue("processing-time-sum")(_.getString)
            .getOrElse(defaultCached.processingTimeSum)

          val sentMessages = clusterMetricsConfig
            .tryValue("sent-messages")(_.getString)
            .getOrElse(defaultCached.sentMessages)

          MetricNames(
            mailboxSize,
            mailboxTimeAvg,
            mailboxTimeMin,
            mailboxTimeMax,
            mailboxTimeSum,
            stashSize,
            receivedMessages,
            processedMessages,
            failedMessages,
            processingTimeAvg,
            processingTimeMin,
            processingTimeMax,
            processingTimeSum,
            sentMessages
          )
        }
        .getOrElse(defaultCached)
    }

  }

  def apply(meter: Meter, config: Config): OpenTelemetryActorMetricsMonitor =
    new OpenTelemetryActorMetricsMonitor(meter, MetricNames.fromConfig(config))

}

class OpenTelemetryActorMetricsMonitor(meter: Meter, metricNames: MetricNames) extends ActorMetricsMonitor {

  private val mailboxSizeObserver = new LongMetricObserverBuilderAdapter[ActorMetricsMonitor.Labels](
    meter
      .longValueObserverBuilder(metricNames.mailboxSize)
      .setDescription("Tracks the size of an Actor's mailbox")
  )

  private val mailboxTimeAvgObserver = new LongMetricObserverBuilderAdapter[ActorMetricsMonitor.Labels](
    meter
      .longValueObserverBuilder(metricNames.mailboxTimeAvg)
      .setDescription("Tracks the average time of an message in an Actor's mailbox")
  )

  private val mailboxTimeMinObserver = new LongMetricObserverBuilderAdapter[ActorMetricsMonitor.Labels](
    meter
      .longValueObserverBuilder(metricNames.mailboxTimeMin)
      .setDescription("Tracks the minimum time of an message in an Actor's mailbox")
  )

  private val mailboxTimeMaxObserver = new LongMetricObserverBuilderAdapter[ActorMetricsMonitor.Labels](
    meter
      .longValueObserverBuilder(metricNames.mailboxTimeMax)
      .setDescription("Tracks the maximum time of an message in an Actor's mailbox")
  )

  private val mailboxTimeSumObserver = new LongMetricObserverBuilderAdapter[ActorMetricsMonitor.Labels](
    meter
      .longValueObserverBuilder(metricNames.mailboxTimeSum)
      .setDescription("Tracks the sum time of the messages in an Actor's mailbox")
  )

  private val stashSizeCounter = new LongSumObserverBuilderAdapter[ActorMetricsMonitor.Labels](
    meter
      .longSumObserverBuilder(metricNames.stashedMessages)
      .setDescription("Tracks stash operations on actors")
  )

  private val receivedMessagesSumObserver = new LongSumObserverBuilderAdapter[ActorMetricsMonitor.Labels](
    meter
      .longSumObserverBuilder(metricNames.receivedMessages)
      .setDescription("Tracks the sum of received messages in an Actor")
  )

  private val processedMessagesSumObserver = new LongSumObserverBuilderAdapter[ActorMetricsMonitor.Labels](
    meter
      .longSumObserverBuilder(metricNames.processedMessages)
      .setDescription("Tracks the sum of processed messages in an Actor")
  )

  private val failedMessagesSumObserver = new LongSumObserverBuilderAdapter[ActorMetricsMonitor.Labels](
    meter
      .longSumObserverBuilder(metricNames.failedMessages)
      .setDescription("Tracks the sum of failed messages in an Actor")
  )

  private val processingTimeAvgObserver = new LongMetricObserverBuilderAdapter[ActorMetricsMonitor.Labels](
    meter
      .longValueObserverBuilder(metricNames.processingTimeAvg)
      .setDescription("Tracks the average processing time of an message in an Actor's receive handler")
  )

  private val processingTimeMinObserver = new LongMetricObserverBuilderAdapter[ActorMetricsMonitor.Labels](
    meter
      .longValueObserverBuilder(metricNames.processingTimeMin)
      .setDescription("Tracks the miminum processing time of an message in an Actor's receive handler")
  )

  private val processingTimeMaxObserver = new LongMetricObserverBuilderAdapter[ActorMetricsMonitor.Labels](
    meter
      .longValueObserverBuilder(metricNames.processingTimeMax)
      .setDescription("Tracks the maximum processing time of an message in an Actor's receive handler")
  )

  private val processingTimeSumObserver = new LongMetricObserverBuilderAdapter[ActorMetricsMonitor.Labels](
    meter
      .longValueObserverBuilder(metricNames.processingTimeSum)
      .setDescription("Tracks the sum processing time of an message in an Actor's receive handler")
  )

  private val sentMessagesObserver = new LongSumObserverBuilderAdapter[ActorMetricsMonitor.Labels](
    meter
      .longSumObserverBuilder(metricNames.sentMessages)
      .setDescription("Tracks the sum of sent messages in an Actor")
  )

  def bind(): OpenTelemetryBoundMonitor =
    new OpenTelemetryBoundMonitor

  class OpenTelemetryBoundMonitor
      extends ActorMetricsMonitor.BoundMonitor
      with RegisterRoot
      with SynchronousInstrumentFactory {

    val mailboxSize: MetricObserver[Long, ActorMetricsMonitor.Labels] = mailboxSizeObserver.createObserver(this)

    val mailboxTimeAvg: MetricObserver[Long, ActorMetricsMonitor.Labels] =
      mailboxTimeAvgObserver.createObserver(this)

    val mailboxTimeMin: MetricObserver[Long, ActorMetricsMonitor.Labels] =
      mailboxTimeMinObserver.createObserver(this)

    val mailboxTimeMax: MetricObserver[Long, ActorMetricsMonitor.Labels] =
      mailboxTimeMaxObserver.createObserver(this)

    val mailboxTimeSum: MetricObserver[Long, ActorMetricsMonitor.Labels] =
      mailboxTimeSumObserver.createObserver(this)

    val stashSize: MetricObserver[Long, ActorMetricsMonitor.Labels] =
      stashSizeCounter.createObserver(this)

    val receivedMessages: MetricObserver[Long, ActorMetricsMonitor.Labels] =
      receivedMessagesSumObserver.createObserver(this)

    val processedMessages: MetricObserver[Long, ActorMetricsMonitor.Labels] =
      processedMessagesSumObserver.createObserver(this)

    val failedMessages: MetricObserver[Long, ActorMetricsMonitor.Labels] =
      failedMessagesSumObserver.createObserver(this)

    val processingTimeAvg: MetricObserver[Long, ActorMetricsMonitor.Labels] =
      processingTimeAvgObserver.createObserver(this)

    val processingTimeMin: MetricObserver[Long, ActorMetricsMonitor.Labels] =
      processingTimeMinObserver.createObserver(this)

    val processingTimeMax: MetricObserver[Long, ActorMetricsMonitor.Labels] =
      processingTimeMaxObserver.createObserver(this)

    val processingTimeSum: MetricObserver[Long, ActorMetricsMonitor.Labels] =
      processingTimeSumObserver.createObserver(this)

    val sentMessages: MetricObserver[Long, ActorMetricsMonitor.Labels] =
      sentMessagesObserver.createObserver(this)
  }

}
