package io.scalac.mesmer.agent.akka.actor

import io.scalac.mesmer.agent.akka.actor.impl._
import io.scalac.mesmer.agent.util.i13n._
import io.scalac.mesmer.agent.{ Agent, AgentInstrumentation }
import io.scalac.mesmer.core.model._
import io.scalac.mesmer.core.module.AkkaActorModule
import io.scalac.mesmer.core.support.ModulesSupport
import io.scalac.mesmer.core.util.Timestamp
import io.scalac.mesmer.extension.actor.{ ActorCellDecorator, ActorCellMetrics }

object AkkaActorAgent
    extends InstrumentModuleFactory(AkkaActorModule)
    with AkkaActorModule.All[Agent]
    with AkkaMailboxInstrumentations {

  def agent(config: AkkaActorModule.AkkaActorMetricsDef[Boolean]): Agent =
    Agent.empty ++
      (if (config.mailboxSize) mailboxSize else Agent.empty) ++
      (if (config.mailboxTimeAvg) mailboxTimeAvg else Agent.empty) ++
      (if (config.mailboxTimeMin) mailboxTimeMin else Agent.empty) ++
      (if (config.mailboxTimeMax) mailboxTimeMax else Agent.empty) ++
      (if (config.mailboxTimeSum) mailboxTimeSum else Agent.empty) ++
      (if (config.stashSize) stashSize else Agent.empty) ++
      (if (config.receivedMessages) receivedMessages else Agent.empty) ++
      (if (config.processedMessages) processedMessages else Agent.empty) ++
      (if (config.failedMessages) failedMessages else Agent.empty) ++
      (if (config.processingTimeAvg) processingTimeAvg else Agent.empty) ++
      (if (config.processingTimeMin) processingTimeMin else Agent.empty) ++
      (if (config.processingTimeMax) processingTimeMax else Agent.empty) ++
      (if (config.processingTimeSum) processingTimeSum else Agent.empty) ++
      (if (config.sentMessages) sentMessages else Agent.empty) ++
      (if (config.droppedMessages) droppedMessages else Agent.empty)

  lazy val mailboxSize: Agent = sharedInstrumentation

  lazy val mailboxTimeAvg: Agent = sharedInstrumentation ++ mailboxInstrumentation

  lazy val mailboxTimeMin: Agent = sharedInstrumentation ++ mailboxInstrumentation

  lazy val mailboxTimeMax: Agent = sharedInstrumentation ++ mailboxInstrumentation

  lazy val mailboxTimeSum: Agent = sharedInstrumentation ++ mailboxInstrumentation

  lazy val stashSize: Agent = sharedInstrumentation ++ classicStashInstrumentationAgent ++ stashBufferImplementation

  lazy val receivedMessages: Agent = sharedInstrumentation

  lazy val processedMessages: Agent = sharedInstrumentation

  lazy val failedMessages: Agent = sharedInstrumentation ++ abstractSupervisionInstrumentation

  lazy val processingTimeAvg: Agent = sharedInstrumentation

  lazy val processingTimeMin: Agent = sharedInstrumentation

  lazy val processingTimeMax: Agent = sharedInstrumentation

  lazy val processingTimeSum: Agent = sharedInstrumentation

  lazy val sentMessages: Agent = sharedInstrumentation ++ mailboxTimeSendMessageIncInstrumentation

  lazy val droppedMessages: Agent = sharedInstrumentation ++ boundedQueueAgent

  protected final val supportedModules: SupportedModules =
    SupportedModules(ModulesSupport.akkaActorModule, ModulesSupport.akkaActor)

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
