package akka.stream

import akka.stream.impl.fusing.GraphInterpreter.Connection
import akka.stream.stage.GraphStageLogic
import io.scalac.agent.akka.stream.GraphStageIslandOps.TerminalSink
import io.scalac.core.model.Tag.StageName
import io.scalac.core.model.Tag.StageName.StreamUniqueStageName

object GraphLogicOps {
  implicit class GraphLogicEnh(val logic: GraphStageLogic) extends AnyVal {

    def inConnections(): Array[Connection] =
      logic.portToConn.take(logic.inCount)

    /**
     * Creates StageName tag that is unique across this stream materialization
     * @return
     */
    def streamUniqueStageName: Option[StreamUniqueStageName] =
      logic.attributes.nameLifted.map(name => StageName(name, logic.stageId))

    def inputConnections: Int = logic.inCount

    def outputConnections: Int = logic.inCount

    def isTerminal: Boolean = logic.attributes.contains(TerminalSink)
  }
}
