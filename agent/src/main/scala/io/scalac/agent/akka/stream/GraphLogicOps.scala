package akka.stream

import akka.stream.Attributes.Name
import akka.stream.impl.fusing.GraphInterpreter.Connection
import akka.stream.stage.GraphStageLogic
import io.scalac.core.model.Tag.StageName

object GraphLogicOps {
  implicit class GraphLogicEnh(val logic: GraphStageLogic) extends AnyVal {

    def inConnections(): Array[Connection] =
      logic.portToConn.take(logic.inCount)

    def stageName: StageName =
      StageName(logic.attributes.get[Name](Name(logic.toString)).n)

    /**
     * Creates StageName tag that is unique across this stream materialization
     * @return
     */
    def streamUniqueStageName: StageName = StageName(logic.attributes.get[Name](Name(logic.toString)).n, logic.stageId)

    def inputConnections: Int = logic.inCount

    def outputConnections: Int = logic.inCount
  }
}
