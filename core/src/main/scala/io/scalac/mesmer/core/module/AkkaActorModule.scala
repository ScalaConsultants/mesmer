package io.scalac.mesmer.core.module
import com.typesafe.config.{ Config => TypesafeConfig }

import io.scalac.mesmer.core.model.Version
import io.scalac.mesmer.core.module.Module.Combine
import io.scalac.mesmer.core.module.Module.Traverse
import io.scalac.mesmer.core.util.LibraryInfo.LibraryInfo

sealed trait AkkaActorMetrics extends MetricsModule {
  this: Module =>

  override type Metrics[T] <: AkkaActorMetricsDef[T]

  trait AkkaActorMetricsDef[T] {
    def mailboxSize: T
    def mailboxTimeAvg: T
    def mailboxTimeMin: T
    def mailboxTimeMax: T
    def mailboxTimeSum: T
    def stashSize: T
    def receivedMessages: T
    def processedMessages: T
    def failedMessages: T
    def processingTimeAvg: T
    def processingTimeMin: T
    def processingTimeMax: T
    def processingTimeSum: T
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
    mailboxTimeAvg: T,
    mailboxTimeMin: T,
    mailboxTimeMax: T,
    mailboxTimeSum: T,
    stashSize: T,
    receivedMessages: T,
    processedMessages: T,
    failedMessages: T,
    processingTimeAvg: T,
    processingTimeMin: T,
    processingTimeMax: T,
    processingTimeSum: T,
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

      val mailboxTimeAvg = config
        .tryValue("mailbox-time-avg")(_.getBoolean)
        .getOrElse(defaultConfig.mailboxTimeAvg)

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
        .getOrElse(defaultConfig.stashSize)

      val receivedMessages = config
        .tryValue("received-messages")(_.getBoolean)
        .getOrElse(defaultConfig.receivedMessages)

      val processedMessages = config
        .tryValue("processed-messages")(_.getBoolean)
        .getOrElse(defaultConfig.processedMessages)

      val failedMessages = config
        .tryValue("failed-messages")(_.getBoolean)
        .getOrElse(defaultConfig.failedMessages)

      val processingTimeAvg = config
        .tryValue("processing-time-avg")(_.getBoolean)
        .getOrElse(defaultConfig.processingTimeAvg)

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
        mailboxTimeAvg = mailboxTimeAvg,
        mailboxTimeMin = mailboxTimeMin,
        mailboxTimeMax = mailboxTimeMax,
        mailboxTimeSum = mailboxTimeSum,
        stashSize = stashSize,
        receivedMessages = receivedMessages,
        processedMessages = processedMessages,
        failedMessages = failedMessages,
        processingTimeAvg = processingTimeAvg,
        processingTimeMin = processingTimeMin,
        processingTimeMax = processingTimeMax,
        processingTimeSum = processingTimeSum,
        sentMessages = sentMessages,
        droppedMessages = droppedMessages
      )
    } else
      Impl[Boolean](false, false, false, false, false, false, false, false, false, false, false, false, false, false,
        false)

  }

  final case class Jars[T](akkaActor: T, akkaActorTyped: T)

  def jarsFromLibraryInfo(info: LibraryInfo): Option[AkkaJar[Version]] =
    for {
      actor      <- info.get(requiredAkkaJars.akkaActor)
      actorTyped <- info.get(requiredAkkaJars.akkaActorTyped)
    } yield Jars(actor, actorTyped)

  val requiredAkkaJars: Jars[String] = Jars("akka-actor", "akka-actor-typed")

  /**
   * Combines config that with AND operator
   */
  implicit val combineConfig: Combine[All[Boolean]] = (first, second) => {
    Impl(
      mailboxSize = first.mailboxSize && second.mailboxSize,
      mailboxTimeAvg = first.mailboxTimeAvg && second.mailboxTimeAvg,
      mailboxTimeMin = first.mailboxTimeMin && second.mailboxTimeMin,
      mailboxTimeMax = first.mailboxTimeMax && second.mailboxTimeMax,
      mailboxTimeSum = first.mailboxTimeSum && second.mailboxTimeSum,
      stashSize = first.stashSize && second.stashSize,
      receivedMessages = first.receivedMessages && second.receivedMessages,
      processedMessages = first.processedMessages && second.processedMessages,
      failedMessages = first.failedMessages && second.failedMessages,
      processingTimeAvg = first.processingTimeAvg && second.processingTimeAvg,
      processingTimeMin = first.processingTimeMin && second.processingTimeMin,
      processingTimeMax = first.processingTimeMax && second.processingTimeMax,
      processingTimeSum = first.processingTimeSum && second.processingTimeSum,
      sentMessages = first.sentMessages && second.sentMessages,
      droppedMessages = first.droppedMessages && second.droppedMessages
    )
  }

  implicit val traverseAll: Traverse[All] = new Traverse[All] {
    def sequence[T](obj: AkkaActorModule.AkkaActorMetricsDef[T]): Seq[T] = Seq(
      obj.mailboxSize,
      obj.mailboxTimeAvg,
      obj.mailboxTimeMin,
      obj.mailboxTimeMax,
      obj.mailboxTimeSum,
      obj.stashSize,
      obj.receivedMessages,
      obj.processedMessages,
      obj.failedMessages,
      obj.processingTimeAvg,
      obj.processingTimeMin,
      obj.processingTimeMax,
      obj.processingTimeSum,
      obj.sentMessages,
      obj.droppedMessages
    )
  }
}
