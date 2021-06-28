package io.scalac.mesmer.agent.akka.persistence.impl

import io.scalac.mesmer.core.util.ReflectionFieldUtils
import java.lang.invoke.MethodHandle

private[impl] trait PersistenceUtils {

//  protected val (elo, melon) = ("String", "elo")

  protected lazy val replayingSnapshotsSetupGetter: MethodHandle =
    ReflectionFieldUtils.getGetter("akka.persistence.typed.internal.ReplayingSnapshot", "setup")

  protected lazy val replayingEventsSetupGetter: MethodHandle =
    ReflectionFieldUtils.getGetter("akka.persistence.typed.internal.ReplayingEvents", "setup")

  protected lazy val behaviorSetupPersistenceId: MethodHandle =
    ReflectionFieldUtils.getGetter("akka.persistence.typed.internal.BehaviorSetup", "persistenceId")

}
