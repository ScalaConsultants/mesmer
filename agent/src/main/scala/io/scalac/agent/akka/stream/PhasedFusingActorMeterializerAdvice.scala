package io.scalac.agent.akka.stream

import net.bytebuddy.asm.Advice.{ Argument, _ }

class PhasedFusingActorMeterializerAdvice
object PhasedFusingActorMeterializerAdvice {

  @OnMethodEnter
  def getPhases(@Argument(1) actorName: String): Unit =
    println(s"!!!!!!! Materializing stream with actor ${actorName} !!!!!!!!")
}
