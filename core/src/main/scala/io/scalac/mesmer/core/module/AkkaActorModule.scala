package io.scalac.mesmer.core.module
import com.typesafe.config.{ Config => TypesafeConfig }

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

object AkkaActorModule extends MesmerModule with AkkaActorMetrics {
  override type Metrics[T] = AkkaActorMetricsDef[T]

  final case class AkkaActorModuleConfig(
    mailboxSize: Boolean,
    mailboxTimeAvg: Boolean,
    mailboxTimeMin: Boolean,
    mailboxTimeMax: Boolean,
    mailboxTimeSum: Boolean,
    stashSize: Boolean,
    receivedMessages: Boolean,
    processedMessages: Boolean,
    failedMessages: Boolean,
    processingTimeAvg: Boolean,
    processingTimeMin: Boolean,
    processingTimeMax: Boolean,
    processingTimeSum: Boolean,
    sentMessages: Boolean,
    droppedMessages: Boolean
  ) extends AkkaActorMetricsDef[Boolean]
      with ModuleConfig {
    lazy val enabled: Boolean = {
      mailboxSize || mailboxTimeAvg || mailboxTimeMin ||
      mailboxTimeMax ||
      mailboxTimeSum ||
      stashSize ||
      receivedMessages ||
      processedMessages ||
      failedMessages ||
      processingTimeAvg ||
      processingTimeMin ||
      processingTimeMax ||
      processingTimeSum ||
      sentMessages ||
      droppedMessages
    }
  }

  val name: String = "akka-actor"

  protected lazy val defaultConfig: Config =
    AkkaActorModuleConfig(true, true, true, true, true, true, true, true, true, true, true, true, true, true, true)

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

      AkkaActorModuleConfig(
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
      AkkaActorModuleConfig(false, false, false, false, false, false, false, false, false, false, false, false, false,
        false, false)

  }

  override type All[T] = Metrics[T]
}
