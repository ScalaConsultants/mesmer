package akka

import akka.stream.impl.ExtendedActorMaterializer
import akka.stream.impl.fusing.GraphInterpreter
import akka.stream.impl.fusing.GraphInterpreterShell
import akka.stream.stage.GraphStageLogic

object MesmerMirrorTypes {
  type ActorRefWithCell                = akka.actor.ActorRefWithCell
  type Cell                            = akka.actor.Cell
  type ExtendedActorMaterializerMirror = ExtendedActorMaterializer
  type GraphInterpreterMirror          = GraphInterpreter
  type GraphInterpreterShellMirror     = GraphInterpreterShell
  type GraphStageLogicMirror           = GraphStageLogic
}
