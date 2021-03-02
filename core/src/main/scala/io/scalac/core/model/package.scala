package io.scalac.core

import io.scalac.core.model.Tag._

package object model {

  type ShellInfo = (Array[StageInfo], Array[ConnectionStats])

  case class ConnectionStats(inName: StageName, outName: StageName, pull: Long, push: Long)

  case class StageInfo(stageName: StageName, subStreamName: SubStreamName)

}
