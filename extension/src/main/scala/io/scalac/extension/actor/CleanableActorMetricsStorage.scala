package io.scalac.extension.actor

import io.scalac.core.util.Timestamp
import io.scalac.extension.config.CleaningSettings
import io.scalac.extension.model.ActorKey
import io.scalac.extension.resource.MutableCleanableStorage

import scala.collection.mutable

class CleanableActorMetricsStorage private (
  buffer: mutable.Map[ActorKey, ActorMetrics],
  override val cleaningConfig: CleaningSettings
) extends MutableActorMetricsStorage(buffer)
    with MutableCleanableStorage[ActorKey, ActorMetrics] {
  override protected def extractTimestamp(value: ActorMetrics): Timestamp = value.timestamp
}
object CleanableActorMetricsStorage {
  def withConfig(cleaningConfig: CleaningSettings): CleanableActorMetricsStorage =
    new CleanableActorMetricsStorage(mutable.Map.empty, cleaningConfig)
}
