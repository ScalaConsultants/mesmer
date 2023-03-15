package io.scalac.mesmer.otelextension.instrumentations.akka.stream

import akka.actor.ActorRef

import scala.collection.mutable

import io.scalac.mesmer.core.model.StreamInfo

trait StreamSnapshotsService {

  def loadInfo(ref: ActorRef, streamInfo: StreamInfo): Unit

  def getSnapshot(): Map[ActorRef, StreamInfo]

}

object StreamSnapshotsService {

  def make(): StreamSnapshotsService = new StreamSnapshotsService {

    private val collectedStreamData: mutable.Map[ActorRef, StreamInfo] = mutable.Map.empty

    override def loadInfo(ref: ActorRef, streamInfo: StreamInfo): Unit = collectedStreamData.put(ref, streamInfo)

    override def getSnapshot(): Map[ActorRef, StreamInfo] = {
      val streamDataSoFar: Map[ActorRef, StreamInfo] = collectedStreamData.toMap
      collectedStreamData.clear()
      streamDataSoFar
    }
  }
}
