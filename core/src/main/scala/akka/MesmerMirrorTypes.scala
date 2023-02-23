package akka

import akka.stream.impl.ExtendedActorMaterializer
import akka.stream.impl.fusing.GraphInterpreter
import akka.stream.impl.fusing.GraphInterpreter.Connection
import akka.stream.impl.fusing.GraphInterpreterShell
import akka.stream.stage.GraphStageLogic

object MesmerMirrorTypes {
  type ActorRefWithCell                = akka.actor.ActorRefWithCell
  type ActorSystemImpl                 = akka.actor.ActorSystemImpl
  type Cell                            = akka.actor.Cell
  type ExtendedActorMaterializerMirror = ExtendedActorMaterializer
  type GraphInterpreterMirror          = GraphInterpreter
  type ConnectionMirror                = Connection
  type GraphInterpreterShellMirror     = GraphInterpreterShell
  type GraphStageLogicMirror           = GraphStageLogic
}
