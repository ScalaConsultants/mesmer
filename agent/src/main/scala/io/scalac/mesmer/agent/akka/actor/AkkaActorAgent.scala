package io.scalac.mesmer.agent.akka.actor

import io.scalac.mesmer.agent.{ Agent, AgentInstrumentation }
import io.scalac.mesmer.agent.akka.actor.impl._
import io.scalac.mesmer.agent.util.i13n._
import io.scalac.mesmer.core.actor.{ ActorCellDecorator, ActorCellMetrics }
import io.scalac.mesmer.core.akka.version26x
import io.scalac.mesmer.core.model._
import io.scalac.mesmer.core.module.AkkaActorModule
import io.scalac.mesmer.core.util.Timestamp
import net.bytebuddy.asm.Advice
import net.bytebuddy.dynamic.TargetType
import net.bytebuddy.implementation.{ FixedValue, MethodCall, MethodDelegation, SuperMethodCall }

object AkkaActorAgent
    extends InstrumentModuleFactory(AkkaActorModule)
    with AkkaActorModule.All[AkkaActorModule.Jars[Version] => Agent]
    with AkkaMailboxInstrumentations {

  /**
   * @param config
   *   configuration of features that are wanted by the user
   * @param jars
   *   versions of required jars to deduce which features can be enabled
   * @return
   *   Resulting agent and resulting configuration based on runtime properties
   */
  def agent(
    jars: AkkaActorModule.AkkaActorJars[Version]
  ): Agent = {

    import module.enabled

    AkkaActorModule.enabled
    val mailboxSizeAgent         = if (enabled.mailboxSize) mailboxSize(jars) else Agent.empty
    val mailboxTimeMinAgent      = if (enabled.mailboxTimeMin) mailboxTimeMin(jars) else Agent.empty
    val mailboxTimeMaxAgent      = if (enabled.mailboxTimeMax) mailboxTimeMax(jars) else Agent.empty
    val mailboxTimeSumAgent      = if (enabled.mailboxTimeSum) mailboxTimeSum(jars) else Agent.empty
    val mailboxTimeCountAgent    = if (enabled.mailboxTimeCount) mailboxTimeCount(jars) else Agent.empty
    val stashedMessagesAgent     = if (enabled.stashedMessages) stashedMessages(jars) else Agent.empty
    val receivedMessagesAgent    = if (enabled.receivedMessages) receivedMessages(jars) else Agent.empty
    val processedMessagesAgent   = if (enabled.processedMessages) processedMessages(jars) else Agent.empty
    val failedMessagesAgent      = if (enabled.failedMessages) failedMessages(jars) else Agent.empty
    val processingTimeMinAgent   = if (enabled.processingTimeMin) processingTimeMin(jars) else Agent.empty
    val processingTimeMaxAgent   = if (enabled.processingTimeMax) processingTimeMax(jars) else Agent.empty
    val processingTimeSumAgent   = if (enabled.processingTimeSum) processingTimeSum(jars) else Agent.empty
    val processingTimeCountAgent = if (enabled.processingTimeCount) processingTimeCount(jars) else Agent.empty
    val sentMessagesAgent        = if (enabled.sentMessages) sentMessages(jars) else Agent.empty
    val droppedMessagesAgent     = if (enabled.droppedMessages) droppedMessages(jars) else Agent.empty

    val resultantAgent =
      mailboxSizeAgent ++
        mailboxTimeMinAgent ++
        mailboxTimeMaxAgent ++
        mailboxTimeSumAgent ++
        mailboxTimeCountAgent ++
        stashedMessagesAgent ++
        receivedMessagesAgent ++
        processedMessagesAgent ++
        failedMessagesAgent ++
        processingTimeMinAgent ++
        processingTimeMaxAgent ++
        processingTimeSumAgent ++
        processingTimeCountAgent ++
        sentMessagesAgent ++
        droppedMessagesAgent

    resultantAgent
  }
  private def ifSupported(versions: AkkaActorModule.Jars[Version])(agent: => Agent): Agent =
    if (version26x.supports(versions.akkaActor) && version26x.supports(versions.akkaActorTyped)) {
      agent
    } else Agent.empty

  lazy val mailboxSize: AkkaActorModule.Jars[Version] => Agent = versions =>
    ifSupported(versions)(sharedInstrumentation)

  lazy val mailboxTimeMin: AkkaActorModule.Jars[Version] => Agent = versions =>
    ifSupported(versions)(
      sharedInstrumentation ++ mailboxInstrumentation ++ initField("mailbox-time", _.initMailboxTimeAgg())
    )

  lazy val mailboxTimeMax: AkkaActorModule.Jars[Version] => Agent = versions =>
    ifSupported(versions)(
      sharedInstrumentation ++ mailboxInstrumentation ++ initField("mailbox-time", _.initMailboxTimeAgg())
    )

  lazy val mailboxTimeSum: AkkaActorModule.Jars[Version] => Agent = versions =>
    ifSupported(versions)(
      sharedInstrumentation ++ mailboxInstrumentation ++ initField("mailbox-time", _.initMailboxTimeAgg())
    )

  lazy val mailboxTimeCount: AkkaActorModule.Jars[Version] => Agent = versions =>
    ifSupported(versions)(
      sharedInstrumentation ++ mailboxInstrumentation ++ initField("mailbox-time", _.initMailboxTimeAgg())
    )

  lazy val stashedMessages: AkkaActorModule.Jars[Version] => Agent = versions =>
    ifSupported(versions)(sharedInstrumentation ++ classicStashInstrumentationAgent ++ stashBufferImplementation)

  lazy val receivedMessages: AkkaActorModule.Jars[Version] => Agent = versions =>
    ifSupported(versions)(sharedInstrumentation ++ initField("received-messages", _.initReceivedMessages()))

  lazy val processedMessages: AkkaActorModule.Jars[Version] => Agent = versions =>
    ifSupported(versions)(
      sharedInstrumentation ++ initField(
        "processed-messages",
        _.initUnhandledMessages()
      )
    )

  lazy val failedMessages: AkkaActorModule.Jars[Version] => Agent = versions =>
    ifSupported(versions)(
      sharedInstrumentation ++ abstractSupervisionInstrumentation ++ initField(
        "mailbox-size",
        metrics => {
          metrics.initFailedMessages()
          metrics.initExceptionHandledMarker()
        }
      )
    )

  lazy val processingTimeMin: AkkaActorModule.Jars[Version] => Agent = versions =>
    ifSupported(versions)(
      sharedInstrumentation ++ initField(
        "processing-time",
        metrics => {
          metrics.initProcessingTimeAgg()
          metrics.initProcessingTimer()
        }
      )
    )

  lazy val processingTimeMax: AkkaActorModule.Jars[Version] => Agent = versions =>
    ifSupported(versions)(
      sharedInstrumentation ++ initField(
        "processing-time",
        metrics => {
          metrics.initProcessingTimeAgg()
          metrics.initProcessingTimer()
        }
      )
    )

  lazy val processingTimeSum: AkkaActorModule.Jars[Version] => Agent = versions =>
    ifSupported(versions)(
      sharedInstrumentation ++ initField(
        "processing-time",
        metrics => {
          metrics.initProcessingTimeAgg()
          metrics.initProcessingTimer()
        }
      )
    )

  lazy val processingTimeCount: AkkaActorModule.Jars[Version] => Agent = versions =>
    ifSupported(versions)(
      sharedInstrumentation ++ initField(
        "processing-time",
        metrics => {
          metrics.initProcessingTimeAgg()
          metrics.initProcessingTimer()
        }
      )
    )

  lazy val sentMessages: AkkaActorModule.Jars[Version] => Agent = versions =>
    ifSupported(versions)(
      sharedInstrumentation ++ mailboxTimeSendMessageIncInstrumentation ++ initField(
        "sent-messages",
        _.initSentMessages()
      )
    )

  lazy val droppedMessages: AkkaActorModule.Jars[Version] => Agent = versions =>
    ifSupported(versions)(sharedInstrumentation ++ boundedQueueAgent ++ initDroppedMessages)

  /**
   * Instrumentation for classic stash
   */
  private val classicStashInstrumentationAgent = {

    val instrumentationPrefix = "classic_stash_instrumentation"

    val stashLogic =
      instrument("akka.actor.StashSupport".fqcnWithTags(s"${instrumentationPrefix}_stash_prepend_operation"))
        .visit(ClassicStashInstrumentationStash, "stash")
        .visit(ClassicStashInstrumentationPrepend, "prepend")

    val stashConstructor =
      instrument(hierarchy("akka.actor.StashSupport".fqcnWithTags("stash_support_constructor")).concreteOnly)
        .visit(StashConstructorAdvice, constructor)

    Agent(stashLogic, stashConstructor)
  }

  private val mailboxInstrumentation = {

    val mailboxTag = "mailbox_time"

    /**
     * Instrumentation that enrich [[akka.dispatch.Envelope]] with additional timestamp field
     */
    val mailboxTimeTimestampInstrumentation =
      instrument("akka.dispatch.Envelope".fqcn)
        .defineField[Timestamp](EnvelopeDecorator.TimestampVarName)

    /**
     * Instrumentation that sets envelope timestamp to current time on each dispatch
     */
    val mailboxTimeSendMessageInstrumentation =
      instrument("akka.actor.dungeon.Dispatch".fqcnWithTags(mailboxTag))
        .visit(
          ActorCellSendMessageTimestampInstrumentation,
          method("sendMessage").takesArgument(0, "akka.dispatch.Envelope")
        )

    /**
     * Instrumentation that computes time in mailbox
     */
    val mailboxTimeDequeueInstrumentation =
      instrument("akka.dispatch.Mailbox".fqcnWithTags(mailboxTag))
        .visit(MailboxDequeueInstrumentation, "dequeue")

    Agent(mailboxTimeTimestampInstrumentation, mailboxTimeSendMessageInstrumentation, mailboxTimeDequeueInstrumentation)

  }

  /**
   * Instrumentation that increase send messages on each dispatch
   */
  private val mailboxTimeSendMessageIncInstrumentation =
    instrument("akka.actor.dungeon.Dispatch".fqcnWithTags("send_message"))
      .visit(
        ActorCellSendMessageMetricInstrumentation,
        method("sendMessage").takesArgument(0, "akka.dispatch.Envelope")
      )

  /**
   * Instrumentation that add [[ActorCellMetrics]] field to [[akka.actor.ActorCell]] and initialize it in `init` method
   */
  private val actorCellInstrumentation = AgentInstrumentation.deferred(
    instrument("akka.actor.ActorCell".fqcnWithTags("metrics"))
      .defineField[ActorCellMetrics](ActorCellDecorator.fieldName)
      .defineMethod("setupMetrics", TargetType.DESCRIPTION, FixedValue.self())
      .intercept(
        named("init").method,
        Advice
          .to(classOf[ActorCellInitAdvice])
          .wrap(
            SuperMethodCall.INSTANCE.andThen(MethodCall.invoke(named("setupMetrics").method))
          )
      )
      .visit(ActorCellReceiveMessageInstrumentation, "receiveMessage")
  )

  private def initField(name: String, init: ActorCellMetrics => Unit): AgentInstrumentation =
    AgentInstrumentation.deferred(
      instrument("akka.actor.ActorCell".fqcnWithTags(name))
        .intercept(
          "setupMetrics",
          MethodDelegation.to(new ActorCellInitializer(init)).andThen(SuperMethodCall.INSTANCE)
        )
    )

  private lazy val sharedInstrumentation: Agent =
    Agent(
      actorCellInstrumentation,
      localActorRefProviderInstrumentation,
      receiveUnhandledInstrumentation
    )

  /**
   * Instrumentation for unhandled metric
   */
  private val receiveUnhandledInstrumentation =
    instrument("akka.actor.Actor".fqcnWithTags("unhandled"))
      .visit(ActorUnhandledInstrumentation, "unhandled")

  /**
   * Instrumentation for supervision
   */
  private val abstractSupervisionInstrumentation =
    instrument(
      hierarchy("akka.actor.typed.internal.AbstractSupervisor".fqcnWithTags("supervision"))
        .overrides("handleReceiveException")
    ).visit(SupervisorHandleReceiveExceptionInstrumentation, "handleReceiveException")

  /**
   * Instrumentation for [[akka.actor.typed.internal.StashBufferImpl]] - collection used for typed stash implementation
   */
  private val stashBufferImplementation =
    instrument(hierarchy("akka.actor.typed.internal.StashBufferImpl".fqcnWithTags("typed_stash_buffer")))
      .visit(StashBufferAdvice, "stash")

  private val initDroppedMessages = instrument("akka.actor.ActorCell".fqcnWithTags("dropped-messages"))
    .visit(ActorCellDroppedMessagesAdvice, "init")

  /**
   * Instrumentation to publish events when new actor is created. This must be enabled for any other instrumentation
   * here to work.
   */
  private val localActorRefProviderInstrumentation: AgentInstrumentation =
    instrument("akka.actor.LocalActorRefProvider".fqcnWithTags("create"))
      .visit(LocalActorRefProviderAdvice, "actorOf")

}
