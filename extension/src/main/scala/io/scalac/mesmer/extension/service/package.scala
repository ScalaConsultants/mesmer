package io.scalac.mesmer.extension

import akka.actor.typed.receptionist.ServiceKey

import io.scalac.mesmer.extension.service.ActorTreeService.Command

package object service {

  val actorTreeServiceKey: ServiceKey[Command] = ServiceKey[Command]("io.scalac.mesmer.extension.ActorTreeService")
}
