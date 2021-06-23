package akka

import akka.stream.impl.ExtendedActorMaterializer
import akka.stream.impl.fusing.{ GraphInterpreter, GraphInterpreterShell }
import akka.stream.stage.GraphStageLogic
import akka.util.{ OptionVal => AkkaOptionVal }

object AkkaMirrorTypes {

  type ExtendedActorMaterializerMirror = ExtendedActorMaterializer
  type GraphInterpreterMirror          = GraphInterpreter
  type GraphInterpreterShellMirror     = GraphInterpreterShell
  type GraphStageLogicMirror           = GraphStageLogic
  type OptionVal[+T >: Null]           = AkkaOptionVal[T]

}
