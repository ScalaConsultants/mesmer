package io.scalac.mesmer.extension.actor

import scala.collection.mutable

import io.scalac.mesmer.core.model.ActorKey
import io.scalac.mesmer.core.util.Timestamp
import io.scalac.mesmer.extension.config.CleaningSettings
import io.scalac.mesmer.extension.resource.MutableCleanableStorage

class CleanableActorMetricsStorage private (
  buffer: mutable.Map[ActorKey, ActorMetrics],
  val cleaningConfig: CleaningSettings
) extends MutableActorMetricsStorage(buffer)
    with MutableCleanableStorage[ActorKey, ActorMetrics] {
  protected def extractTimestamp(value: ActorMetrics): Timestamp = value.timestamp
}
object CleanableActorMetricsStorage {
  def withConfig(cleaningConfig: CleaningSettings): CleanableActorMetricsStorage =
    new CleanableActorMetricsStorage(mutable.Map.empty, cleaningConfig)
}
