package io.scalac.mesmer.zio

import zio.ZIO

object ZIOSupervision {

  var supervisorAlreadyAdded: Boolean = false // make thread safe

  def setAlreadyAdded(): Unit =
    supervisorAlreadyAdded = true

  def addMesmerSupervisor(zio: ZIO[_, _, _]): ZIO[_, _, _] = zio.supervised(new MesmerSupervisor())
}
