package io.scalac.mesmer.core.model.stream

import scala.annotation.tailrec
import scala.collection.mutable
import scala.jdk.CollectionConverters._

final class ConnectionsIndexCache(
  val indexCache: mutable.Map[StageInfo, ConnectionsIndexCache.IndexCacheEntry]
) {
  import ConnectionsIndexCache._

  def get(stage: StageInfo)(connections: Array[ConnectionStats]): IndexData = indexCache
    .get(stage)
    .fold {
      val (wiredInputs, wiredOutputs, entry) = findWithIndex(stage, connections)
      indexCache.put(stage, entry)
      IndexData(DirectionData(wiredInputs, entry.distinctInputs), DirectionData(wiredOutputs, entry.distinctOutputs))
    }(entry =>
      IndexData(
        DirectionData(entry.inputs.map(connections.apply), entry.distinctInputs),
        DirectionData(entry.outputs.map(connections.apply), entry.distinctOutputs)
      )
    )

  private def findWithIndex(
    stage: StageInfo,
    connections: Array[ConnectionStats]
  ): (Set[ConnectionStats], Set[ConnectionStats], IndexCacheEntry) = {
    val inputIndexSet: mutable.Set[Int]                    = mutable.Set.empty
    val outputIndexSet: mutable.Set[Int]                   = mutable.Set.empty
    val inputConnectionsSet: mutable.Set[ConnectionStats]  = mutable.Set.empty
    val outputConnectionsSet: mutable.Set[ConnectionStats] = mutable.Set.empty

    val inputOutputIds = mutable.Set.empty[Int]
    val outputInputIds = mutable.Set.empty[Int]

    var distinctOutput = true
    var distinctInput  = true

    @tailrec
    def findInArray(index: Int): (Set[ConnectionStats], Set[ConnectionStats], IndexCacheEntry) =
      if (index >= connections.length)
        (
          inputConnectionsSet.toSet,
          outputConnectionsSet.toSet,
          IndexCacheEntry(inputIndexSet.toSet, outputIndexSet.toSet, distinctInput, distinctOutput)
        )
      else {
        val connection = connections(index)
        if (connection.in == stage.id) {
          inputConnectionsSet += connection
          inputIndexSet += index

          if (distinctInput) {
            if (inputOutputIds.contains(connection.out)) {
              distinctInput = false
            } else {
              inputOutputIds += connection.out
            }
          }

        } else if (connection.out == stage.id) {
          outputConnectionsSet += connection
          outputIndexSet += index

          if (distinctOutput) {
            if (outputInputIds.contains(connection.in)) {
              distinctOutput = false
            } else {
              outputInputIds += connection.in
            }
          }

        }
        findInArray(index + 1)
      }
    findInArray(0)
  }
}

object ConnectionsIndexCache {
  final case class IndexCacheEntry(
    inputs: Set[Int],
    outputs: Set[Int],
    distinctInputs: Boolean,
    distinctOutputs: Boolean
  )

  def bounded(entries: Int): ConnectionsIndexCache = {

    val mutableMap: mutable.Map[StageInfo, IndexCacheEntry] =
      new java.util.LinkedHashMap[StageInfo, IndexCacheEntry](entries, 0.75f, true) {
        override def removeEldestEntry(eldest: java.util.Map.Entry[StageInfo, IndexCacheEntry]): Boolean =
          this.size() >= entries
      }.asScala

    new ConnectionsIndexCache(mutableMap)
  }
}
