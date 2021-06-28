package io.scalac.mesmer.agent.akka.actor

import io.scalac.mesmer.agent.Agent
import io.scalac.mesmer.agent.AgentInstrumentation
import io.scalac.mesmer.agent.akka.actor.impl._
import io.scalac.mesmer.agent.util.i13n._
import io.scalac.mesmer.core.model._
import io.scalac.mesmer.core.module.AkkaActorModule
import io.scalac.mesmer.core.util.Timestamp
import io.scalac.mesmer.extension.actor.ActorCellDecorator
import io.scalac.mesmer.extension.actor.ActorCellMetrics

object AkkaActorAgent
    extends InstrumentModuleFactory(AkkaActorModule)
    with AkkaActorModule.All[AkkaActorModule.AkkaJar[Version] => Option[Agent]]
    with AkkaMailboxInstrumentations {

  /**
   * @param config configuration of features that are wanted by the user
   * @param jars   versions of required jars to deduce which features can be enabled
   * @return Resulting agent and resulting configuration based on runtime properties
   */
  override protected def agent(
    config: AkkaActorModule.All[Boolean],
    jars: AkkaActorModule.Jars[Version]
  ): (Agent, AkkaActorModule.All[Boolean]) = {
    val mailboxSizeAgent         = if (config.mailboxSize) mailboxSize(jars) else None
    val mailboxTimeMinAgent      = if (config.mailboxTimeMin) mailboxTimeMin(jars) else None
    val mailboxTimeMaxAgent      = if (config.mailboxTimeMax) mailboxTimeMax(jars) else None
    val mailboxTimeSumAgent      = if (config.mailboxTimeSum) mailboxTimeSum(jars) else None
    val mailboxTimeCountAgent    = if (config.mailboxTimeCount) mailboxTimeCount(jars) else None
    val stashSizeAgent           = if (config.stashSize) stashSize(jars) else None
    val receivedMessagesAgent    = if (config.receivedMessages) receivedMessages(jars) else None
    val processedMessagesAgent   = if (config.processedMessages) processedMessages(jars) else None
    val failedMessagesAgent      = if (config.failedMessages) failedMessages(jars) else None
    val processingTimeMinAgent   = if (config.processingTimeMin) processingTimeMin(jars) else None
    val processingTimeMaxAgent   = if (config.processingTimeMax) processingTimeMax(jars) else None
    val processingTimeSumAgent   = if (config.processingTimeSum) processingTimeSum(jars) else None
    val processingTimeCountAgent = if (config.processingTimeCount) processingTimeCount(jars) else None
    val sentMessagesAgent        = if (config.sentMessages) sentMessages(jars) else None
    val droppedMessagesAgent     = if (config.droppedMessages) droppedMessages(jars) else None

    val resultantAgent = mailboxSizeAgent.getOrElse(Agent.empty) ++
      mailboxTimeMinAgent.getOrElse(Agent.empty) ++
      mailboxTimeMaxAgent.getOrElse(Agent.empty) ++
      mailboxTimeSumAgent.getOrElse(Agent.empty) ++
      mailboxTimeCountAgent.getOrElse(Agent.empty) ++
      stashSizeAgent.getOrElse(Agent.empty) ++
      receivedMessagesAgent.getOrElse(Agent.empty) ++
      processedMessagesAgent.getOrElse(Agent.empty) ++
      failedMessagesAgent.getOrElse(Agent.empty) ++
      processingTimeMinAgent.getOrElse(Agent.empty) ++
      processingTimeMaxAgent.getOrElse(Agent.empty) ++
      processingTimeSumAgent.getOrElse(Agent.empty) ++
      processingTimeCountAgent.getOrElse(Agent.empty) ++
      sentMessagesAgent.getOrElse(Agent.empty) ++
      droppedMessagesAgent.getOrElse(Agent.empty)

    val enabled = AkkaActorModule.Impl(
      mailboxSize = mailboxSizeAgent.isDefined,
      mailboxTimeMin = mailboxTimeMinAgent.isDefined,
      mailboxTimeMax = mailboxTimeMaxAgent.isDefined,
      mailboxTimeSum = mailboxTimeSumAgent.isDefined,
      mailboxTimeCount = mailboxTimeCountAgent.isDefined,
      stashSize = stashSizeAgent.isDefined,
      receivedMessages = receivedMessagesAgent.isDefined,
      processedMessages = processedMessagesAgent.isDefined,
      failedMessages = failedMessagesAgent.isDefined,
      processingTimeMin = processingTimeMinAgent.isDefined,
      processingTimeMax = processingTimeMaxAgent.isDefined,
      processingTimeSum = processingTimeSumAgent.isDefined,
      processingTimeCount = processingTimeCountAgent.isDefined,
      sentMessages = sentMessagesAgent.isDefined,
      droppedMessages = droppedMessagesAgent.isDefined
    )

    (resultantAgent, enabled)
  }

  lazy val mailboxSize: AkkaActorModule.AkkaJar[Version] => Option[Agent] = _ => Some(sharedInstrumentation)

  lazy val mailboxTimeMin: AkkaActorModule.AkkaJar[Version] => Option[Agent] = _ =>
    Some(sharedInstrumentation ++ mailboxInstrumentation)

  lazy val mailboxTimeMax: AkkaActorModule.AkkaJar[Version] => Option[Agent] = _ =>
    Some(sharedInstrumentation ++ mailboxInstrumentation)

  lazy val mailboxTimeSum: AkkaActorModule.AkkaJar[Version] => Option[Agent] = _ =>
    Some(sharedInstrumentation ++ mailboxInstrumentation)

  lazy val mailboxTimeCount: AkkaActorModule.AkkaJar[Version] => Option[Agent] = _ =>
    Some(sharedInstrumentation ++ mailboxInstrumentation)

  lazy val stashSize: AkkaActorModule.AkkaJar[Version] => Option[Agent] = _ =>
    Some(sharedInstrumentation ++ classicStashInstrumentationAgent ++ stashBufferImplementation)

  lazy val receivedMessages: AkkaActorModule.AkkaJar[Version] => Option[Agent] = _ => Some(sharedInstrumentation)

  lazy val processedMessages: AkkaActorModule.AkkaJar[Version] => Option[Agent] = _ => Some(sharedInstrumentation)

  lazy val failedMessages: AkkaActorModule.AkkaJar[Version] => Option[Agent] = _ =>
    Some(sharedInstrumentation ++ abstractSupervisionInstrumentation)

  lazy val processingTimeMin: AkkaActorModule.AkkaJar[Version] => Option[Agent] = _ => Some(sharedInstrumentation)

  lazy val processingTimeMax: AkkaActorModule.AkkaJar[Version] => Option[Agent] = _ => Some(sharedInstrumentation)

  lazy val processingTimeSum: AkkaActorModule.AkkaJar[Version] => Option[Agent] = _ => Some(sharedInstrumentation)

  lazy val processingTimeCount: AkkaActorModule.AkkaJar[Version] => Option[Agent] = _ => Some(sharedInstrumentation)

  lazy val sentMessages: AkkaActorModule.AkkaJar[Version] => Option[Agent] = _ =>
    Some(sharedInstrumentation ++ mailboxTimeSendMessageIncInstrumentation)

  lazy val droppedMessages: AkkaActorModule.AkkaJar[Version] => Option[Agent] = _ =>
    Some(sharedInstrumentation ++ boundedQueueAgent)

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
     * Instrumentation that enrich [[ akka.dispatch.Envelope ]] with additional timestamp field
     */
    val mailboxTimeTimestampInstrumentation =
      instrument("akka.dispatch.Envelope".fqcn)
        .defineField[Timestamp](EnvelopeDecorator.TimestampVarName)
    //      .defineField[Boolean](EnvelopeDecorator.TimestampVarName)

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
   * Instrumentation that add [[ ActorCellMetrics ]] field to [[ akka.actor.ActorCell ]]
   * and initialize it in `init` method
   */
  private val actorCellInstrumentation = instrument("akka.actor.ActorCell".fqcnWithTags("metrics"))
    .defineField[ActorCellMetrics](ActorCellDecorator.fieldName)
    .visit(ActorCellConstructorInstrumentation, "init")
    .visit(ActorCellReceiveMessageInstrumentation, "receiveMessage")

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
   * Instrumentation for [[ akka.actor.typed.internal.StashBufferImpl ]] - collection used for typed stash implementation
   */
  private val stashBufferImplementation =
    instrument(hierarchy("akka.actor.typed.internal.StashBufferImpl".fqcnWithTags("typed_stash_buffer")))
      .visit(StashBufferAdvice, "stash")

  /**
   * Instrumentation to publish events when new actor is created. This must be enabled
   * for any other instrumentation here to work.
   */
  private val localActorRefProviderInstrumentation: AgentInstrumentation =
    instrument("akka.actor.LocalActorRefProvider".fqcnWithTags("create"))
      .visit(LocalActorRefProviderAdvice, "actorOf")
}
