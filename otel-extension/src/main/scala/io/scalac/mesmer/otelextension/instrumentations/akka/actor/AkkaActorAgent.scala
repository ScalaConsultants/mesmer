package io.scalac.mesmer.otelextension.instrumentations.akka.actor

import io.scalac.mesmer.agent.Agent
import io.scalac.mesmer.agent.AgentInstrumentation
import io.scalac.mesmer.agent.util.i13n._
import io.scalac.mesmer.core.module.AkkaActorModule
import io.scalac.mesmer.otelextension.instrumentations.akka.actor.impl._

object AkkaActorAgent
    extends InstrumentModuleFactory(AkkaActorModule)
    with AkkaActorModule.All[Agent]
    with AkkaMailboxInstrumentations {

  /**
   * @param config
   *   configuration of features that are wanted by the user
   * @param jars
   *   versions of required jars to deduce which features can be enabled
   * @return
   *   Resulting agent and resulting configuration based on runtime properties
   */
  def agent: Agent = {

    val config = module.enabled

    List(
      mailboxSize.onCondition(config.mailboxSize),
      mailboxTimeMin.onCondition(config.mailboxTimeMin),
      mailboxTimeMax.onCondition(config.mailboxTimeMax),
      mailboxTimeSum.onCondition(config.mailboxTimeSum),
      mailboxTimeCount.onCondition(config.mailboxTimeCount),
      stashedMessages.onCondition(config.stashedMessages),
      receivedMessages.onCondition(config.receivedMessages),
      processedMessages.onCondition(config.processedMessages),
      failedMessages.onCondition(config.failedMessages),
      processingTimeMin.onCondition(config.processingTimeMin),
      processingTimeMax.onCondition(config.processingTimeMax),
      processingTimeSum.onCondition(config.processingTimeSum),
      processingTimeCount.onCondition(config.processingTimeCount),
      sentMessages.onCondition(config.sentMessages),
      droppedMessages.onCondition(config.droppedMessages)
    ).reduce(_ ++ _)
  }

  lazy val mailboxSize: Agent = sharedInstrumentation

  lazy val mailboxTimeMin: Agent =
    sharedInstrumentation ++ mailboxInstrumentation

  lazy val mailboxTimeMax: Agent =
    sharedInstrumentation ++ mailboxInstrumentation

  lazy val mailboxTimeSum: Agent =
    sharedInstrumentation ++ mailboxInstrumentation

  lazy val mailboxTimeCount: Agent =
    sharedInstrumentation ++ mailboxInstrumentation

  lazy val stashedMessages: Agent =
    sharedInstrumentation ++ classicStashInstrumentationAgent ++ stashBufferImplementation

  lazy val receivedMessages: Agent = sharedInstrumentation

  lazy val processedMessages: Agent = sharedInstrumentation

  lazy val failedMessages: Agent = sharedInstrumentation ++ abstractSupervisionInstrumentation

  lazy val processingTimeMin: Agent = sharedInstrumentation

  lazy val processingTimeMax: Agent = sharedInstrumentation

  lazy val processingTimeSum: Agent = sharedInstrumentation

  lazy val processingTimeCount: Agent = sharedInstrumentation

  lazy val sentMessages: Agent = sharedInstrumentation ++ mailboxTimeSendMessageIncInstrumentation

  lazy val droppedMessages: Agent = sharedInstrumentation ++ boundedQueueAgent ++ initDroppedMessages

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

    Agent(mailboxTimeSendMessageInstrumentation, mailboxTimeDequeueInstrumentation)

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
      .visit(ActorMetricsInitAdvice, named("init").method)
      .visit(ActorCellReceiveMessageInstrumentation, "receiveMessage")
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
