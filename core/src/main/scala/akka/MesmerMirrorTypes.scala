package akka

import akka.stream.impl.ExtendedActorMaterializer
import akka.stream.impl.fusing.{ GraphInterpreter, GraphInterpreterShell }
import akka.stream.stage.GraphStageLogic
import akka.util.{ OptionVal => AkkaOptionVal }

object MesmerMirrorTypes {
  type ActorRefWithCell                = akka.actor.ActorRefWithCell
  type Cell                            = akka.actor.Cell
  type ExtendedActorMaterializerMirror = ExtendedActorMaterializer
  type GraphInterpreterMirror          = GraphInterpreter
  type GraphInterpreterShellMirror     = GraphInterpreterShell
  type GraphStageLogicMirror           = GraphStageLogic

  val c: Cell = ???
  c.isLocal
}
