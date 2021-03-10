package io.scalac.core

import io.scalac.core.model.Tag.StageName.StreamUniqueStageName
import io.scalac.core.model.Tag._

package object model {

  type ShellInfo = (Array[StageInfo], Array[ConnectionStats])

  /**
   * All information inside GraphInterpreter should be local to that interpreter
   * meaning that all connections in array are connection between logics owned by same GraphInterpreter
   * MODIFY IF THIS IS NOT TRUE!
   * @param in index of inHandler owner
   * @param out index of outHandler owner
   * @param pull demand to upstream
   * @param push elements pushed to downstream
   */
  case class ConnectionStats(in: Int, out: Int, pull: Long, push: Long)

  case class StageInfo(
    id: Int,
    stageName: StreamUniqueStageName,
    subStreamName: SubStreamName,
    terminal: Boolean = false
  )

}
