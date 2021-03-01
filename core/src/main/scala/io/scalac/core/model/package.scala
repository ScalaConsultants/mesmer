package io.scalac.core

import io.scalac.core.model.Tag.StageName.StreamUniqueStageName
import io.scalac.core.model.Tag._

package object model {

  type ShellInfo = (Array[StageInfo], Array[ConnectionStats])

  case class ConnectionStats(inName: StreamUniqueStageName, outName: StreamUniqueStageName, pull: Long, push: Long)

  case class StageInfo(stageName: StreamUniqueStageName, subStreamName: SubStreamName, terminal: Boolean = false)

}
