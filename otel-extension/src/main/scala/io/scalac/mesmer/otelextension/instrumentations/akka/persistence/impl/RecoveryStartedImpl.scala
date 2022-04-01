package io.scalac.mesmer.otelextension.instrumentations.akka.persistence.impl

import akka.actor.typed.scaladsl.ActorContext
import net.bytebuddy.asm.Advice.OnMethodEnter
import io.scalac.mesmer.core.model._

object RecoveryStartedImpl {

  def enter(
    context: ActorContext[_],
    self: AnyRef
  ): Unit =
    println("RECOVERY STARTED EXTENSION")
  //    val path = context.self.path.toPath
  //
  //    val replayingSnapshotsSetupGetter: MethodHandle =
  //      ReflectionFieldUtils.getGetter("akka.persistence.typed.internal.ReplayingSnapshot", "setup")
  //
  //    val behaviorSetupPersistenceId: MethodHandle =
  //      ReflectionFieldUtils.getGetter("akka.persistence.typed.internal.BehaviorSetup", "persistenceId")
  //
  //    val persistenceIdHandle: MethodHandle =
  //      ReflectionFieldUtils.chain(replayingSnapshotsSetupGetter, behaviorSetupPersistenceId)
  //
  //    val persistenceId = persistenceIdHandle.invoke(self).asInstanceOf[PersistenceId]
  //
  //    EventBus(context.system).publishEvent(RecoveryStarted(path, persistenceId.id, Timestamp.create()))
}
