package akka

import akka.stream.impl.ExtendedActorMaterializer
import akka.stream.impl.fusing.GraphInterpreter

object AkkaMirrorTypes {

  type ExtendedActorMaterializerMirror = ExtendedActorMaterializer
  type GraphInterpreterMirrr = GraphInterpreter
}
