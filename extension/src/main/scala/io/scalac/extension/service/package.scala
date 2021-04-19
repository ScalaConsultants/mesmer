package io.scalac.extension

import akka.actor.typed.receptionist.ServiceKey

import io.scalac.extension.service.ActorTreeService.Command

package object service {

  val actorTreeServiceKey: ServiceKey[Command] = ServiceKey[Command]("io.scalac.extension.ActorTreeService")
}
