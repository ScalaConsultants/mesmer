package io.scalac.core.actor

import scala.collection.mutable

import io.scalac.core.model.ActorKey
import io.scalac.core.util.Timestamp
import io.scalac.extension.config.CleaningSettings
import io.scalac.extension.resource.MutableCleanableStorage

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
