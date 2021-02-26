package io.scalac.core

import io.scalac.core.model.Tag.{ StageName, StreamName }

package object model {

  type ShellInfo = Tuple2[Array[StageInfo], Array[ConnectionStats]]

  case class ConnectionStats(inName: StageName, outName: StageName, pull: Long, push: Long)

  case class StageInfo(stageName: StageName, streamName: StreamName)

}
