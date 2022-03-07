package io.scalac.mesmer.core.module

import io.scalac.mesmer.core.typeclasses.{ Combine, Traverse }

sealed trait AkkaActorMetrics extends MetricsModule {
  this: Module =>

  override type Metrics[T] <: AkkaActorMetricsDef[T]

  trait AkkaActorMetricsDef[T] {
    def mailboxSize: T
    def mailboxTimeMin: T
    def mailboxTimeMax: T
    def mailboxTimeSum: T
    def mailboxTimeCount: T
    def stashedMessages: T
    def receivedMessages: T
    def processedMessages: T
    def failedMessages: T
    def processingTimeMin: T
    def processingTimeMax: T
    def processingTimeSum: T
    def processingTimeCount: T
    def sentMessages: T
    def droppedMessages: T
  }
}

object AkkaActorModule extends MesmerModule with AkkaActorMetrics {
  override type Metrics[T] = AkkaActorMetricsDef[T]
  override type All[T]     = Metrics[T]

  final case class Impl[T](
    mailboxSize: T,
    mailboxTimeMin: T,
    mailboxTimeMax: T,
    mailboxTimeSum: T,
    mailboxTimeCount: T,
    stashedMessages: T,
    receivedMessages: T,
    processedMessages: T,
    failedMessages: T,
    processingTimeMin: T,
    processingTimeMax: T,
    processingTimeSum: T,
    processingTimeCount: T,
    sentMessages: T,
    droppedMessages: T
  ) extends AkkaActorMetricsDef[T]

  lazy val name: String = "akkaactor"

  lazy val defaultConfig: Config =
    Impl[Boolean](true, true, true, true, true, true, true, true, true, true, true, true, true, true, true)

  protected def fromMap(properties: Map[String, Boolean]): AkkaActorModule.Config =
    Impl(
      mailboxSize = properties.getOrElse("mailbox.size", defaultConfig.mailboxSize),
      mailboxTimeMin = properties.getOrElse("mailbox.time.min", defaultConfig.mailboxTimeMin),
      mailboxTimeMax = properties.getOrElse("mailbox.time.max", defaultConfig.mailboxTimeMax),
      mailboxTimeSum = properties.getOrElse("mailbox.time.sum", defaultConfig.mailboxTimeSum),
      mailboxTimeCount = properties.getOrElse("mailbox.time.count", defaultConfig.mailboxTimeCount),
      stashedMessages = properties.getOrElse("stash.size", defaultConfig.stashedMessages),
      receivedMessages = properties.getOrElse("received.messages", defaultConfig.receivedMessages),
      processedMessages = properties.getOrElse("processed.messages", defaultConfig.processedMessages),
      failedMessages = properties.getOrElse("failed.messages", defaultConfig.failedMessages),
      processingTimeMin = properties.getOrElse("processing.time.min", defaultConfig.processingTimeMin),
      processingTimeMax = properties.getOrElse("processing.time.max", defaultConfig.processingTimeMax),
      processingTimeSum = properties.getOrElse("processing.time.sum", defaultConfig.processingTimeSum),
      processingTimeCount = properties.getOrElse("processing.time.count", defaultConfig.processingTimeCount),
      sentMessages = properties.getOrElse("sent.messages", defaultConfig.sentMessages),
      droppedMessages = properties.getOrElse("dropped.messages", defaultConfig.droppedMessages)
    )

  /**
   * Combines config that with AND operator
   */
  implicit val combineConfig: Combine[All[Boolean]] = (first, second) =>
    Impl(
      mailboxSize = first.mailboxSize && second.mailboxSize,
      mailboxTimeMin = first.mailboxTimeMin && second.mailboxTimeMin,
      mailboxTimeMax = first.mailboxTimeMax && second.mailboxTimeMax,
      mailboxTimeSum = first.mailboxTimeSum && second.mailboxTimeSum,
      mailboxTimeCount = first.mailboxTimeCount && second.mailboxTimeCount,
      stashedMessages = first.stashedMessages && second.stashedMessages,
      receivedMessages = first.receivedMessages && second.receivedMessages,
      processedMessages = first.processedMessages && second.processedMessages,
      failedMessages = first.failedMessages && second.failedMessages,
      processingTimeCount = first.processingTimeCount && second.processingTimeCount,
      processingTimeMin = first.processingTimeMin && second.processingTimeMin,
      processingTimeMax = first.processingTimeMax && second.processingTimeMax,
      processingTimeSum = first.processingTimeSum && second.processingTimeSum,
      sentMessages = first.sentMessages && second.sentMessages,
      droppedMessages = first.droppedMessages && second.droppedMessages
    )

  implicit val traverseAll: Traverse[All] = new Traverse[All] {
    def sequence[T](obj: AkkaActorModule.AkkaActorMetricsDef[T]): Seq[T] = Seq(
      obj.mailboxSize,
      obj.mailboxTimeMin,
      obj.mailboxTimeMax,
      obj.mailboxTimeSum,
      obj.mailboxTimeCount,
      obj.stashedMessages,
      obj.receivedMessages,
      obj.processedMessages,
      obj.failedMessages,
      obj.processingTimeMin,
      obj.processingTimeMax,
      obj.processingTimeSum,
      obj.processingTimeCount,
      obj.sentMessages,
      obj.droppedMessages
    )
  }
}
