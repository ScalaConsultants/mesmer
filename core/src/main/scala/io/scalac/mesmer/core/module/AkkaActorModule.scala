package io.scalac.mesmer.core.module
import com.typesafe.config.{ Config => TypesafeConfig }

import io.scalac.mesmer.core.model.Version
import io.scalac.mesmer.core.module.Module.Combine
import io.scalac.mesmer.core.module.Module.JarsNames
import io.scalac.mesmer.core.module.Module.Traverse
import io.scalac.mesmer.core.util.LibraryInfo.LibraryInfo

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

object AkkaActorModule extends MesmerModule with AkkaActorMetrics with RegisterGlobalConfiguration {
  override type Metrics[T] = AkkaActorMetricsDef[T]
  override type All[T]     = Metrics[T]
  override type AkkaJar[T] = Jars[T]

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

  val name: String = "akka-actor"

  lazy val defaultConfig: Config =
    Impl[Boolean](true, true, true, true, true, true, true, true, true, true, true, true, true, true, true)

  protected def extractFromConfig(config: TypesafeConfig): Config = {
    val moduleEnabled = config
      .tryValue("enabled")(_.getBoolean)
      .getOrElse(true)
    if (moduleEnabled) {
      val mailboxSize = config
        .tryValue("mailbox-size")(_.getBoolean)
        .getOrElse(defaultConfig.mailboxSize)

      val mailboxTimeCount = config
        .tryValue("mailbox-time-count")(_.getBoolean)
        .getOrElse(defaultConfig.mailboxTimeCount)

      val mailboxTimeMin = config
        .tryValue("mailbox-time-min")(_.getBoolean)
        .getOrElse(defaultConfig.mailboxTimeMin)

      val mailboxTimeMax = config
        .tryValue("mailbox-time-max")(_.getBoolean)
        .getOrElse(defaultConfig.mailboxTimeMax)

      val mailboxTimeSum = config
        .tryValue("mailbox-time-sum")(_.getBoolean)
        .getOrElse(defaultConfig.mailboxTimeSum)

      val stashSize = config
        .tryValue("stash-size")(_.getBoolean)
        .getOrElse(defaultConfig.stashedMessages)

      val receivedMessages = config
        .tryValue("received-messages")(_.getBoolean)
        .getOrElse(defaultConfig.receivedMessages)

      val processedMessages = config
        .tryValue("processed-messages")(_.getBoolean)
        .getOrElse(defaultConfig.processedMessages)

      val failedMessages = config
        .tryValue("failed-messages")(_.getBoolean)
        .getOrElse(defaultConfig.failedMessages)

      val processingTimeCount = config
        .tryValue("processing-time-count")(_.getBoolean)
        .getOrElse(defaultConfig.processingTimeCount)

      val processingTimeMin = config
        .tryValue("processing-time-min")(_.getBoolean)
        .getOrElse(defaultConfig.processingTimeMin)

      val processingTimeMax = config
        .tryValue("processing-time-max")(_.getBoolean)
        .getOrElse(defaultConfig.processingTimeMax)

      val processingTimeSum = config
        .tryValue("processing-time-sum")(_.getBoolean)
        .getOrElse(defaultConfig.processingTimeSum)

      val sentMessages = config
        .tryValue("sent-messages")(_.getBoolean)
        .getOrElse(defaultConfig.sentMessages)

      val droppedMessages = config
        .tryValue("dropped-messages")(_.getBoolean)
        .getOrElse(defaultConfig.droppedMessages)

      Impl[Boolean](
        mailboxSize = mailboxSize,
        mailboxTimeMin = mailboxTimeMin,
        mailboxTimeMax = mailboxTimeMax,
        mailboxTimeSum = mailboxTimeSum,
        mailboxTimeCount = mailboxTimeCount,
        stashedMessages = stashSize,
        receivedMessages = receivedMessages,
        processedMessages = processedMessages,
        failedMessages = failedMessages,
        processingTimeMin = processingTimeMin,
        processingTimeMax = processingTimeMax,
        processingTimeSum = processingTimeSum,
        processingTimeCount = processingTimeCount,
        sentMessages = sentMessages,
        droppedMessages = droppedMessages
      )
    } else
      Impl[Boolean](false, false, false, false, false, false, false, false, false, false, false, false, false, false,
        false)

  }

  final case class Jars[T](akkaActor: T, akkaActorTyped: T) extends Module.CommonJars[T]

  def jarsFromLibraryInfo(info: LibraryInfo): Option[AkkaJar[Version]] =
    for {
      actor      <- info.get(JarsNames.akkaActor)
      actorTyped <- info.get(JarsNames.akkaActorTyped)
    } yield Jars(actor, actorTyped)

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
