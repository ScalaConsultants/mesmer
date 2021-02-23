package io.scalac.core

import io.scalac.core.model.Tag.StageName

package object model {

  case class ConnectionStats(inName: StageName, outName: StageName, pull: Long, push: Long)

}
