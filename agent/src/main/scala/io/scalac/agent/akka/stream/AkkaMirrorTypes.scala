package akka

import akka.stream.impl.ExtendedActorMaterializer
import akka.stream.impl.fusing.{ GraphInterpreter, GraphInterpreterShell }

object AkkaMirrorTypes {

  type ExtendedActorMaterializerMirror = ExtendedActorMaterializer
  type GraphInterpreterMirrr           = GraphInterpreter
  type GraphInterpreterShellMirror      = GraphInterpreterShell
}
