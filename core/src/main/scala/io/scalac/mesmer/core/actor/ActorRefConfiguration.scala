package io.scalac.mesmer.core.actor

import akka.actor.ClassicActorSystemProvider
import akka.actor.typed.ActorRef
import akka.actor.{ ActorRef => ClassicActorRef }
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.common.AttributesBuilder

import io.scalac.mesmer.core.akka.model.AttributeNames

trait ActorRefConfiguration {

  def forClassic(ref: ClassicActorRef): AttributesBuilder
  def forTyped(ref: ActorRef[_]): AttributesBuilder
}

object DefaultActorRefConfiguration extends ActorRefConfiguration {
  override def forClassic(ref: ClassicActorRef): AttributesBuilder = Attributes
    .builder()
    .put(AttributeNames.ActorPath, ref.path.toStringWithoutAddress)

  override def forTyped(ref: ActorRef[_]): AttributesBuilder = Attributes
    .builder()
    .put(AttributeNames.ActorPath, ref.path.toStringWithoutAddress)

}

final class WithSystemActorRefConfigurator(system: ClassicActorSystemProvider, underlying: ActorRefConfiguration)
    extends ActorRefConfiguration {
  def forClassic(ref: ClassicActorRef): AttributesBuilder = underlying
    .forClassic(ref)
    .put(AttributeNames.ActorSystem, system.classicSystem.name)

  def forTyped(ref: ActorRef[_]): AttributesBuilder = underlying
    .forTyped(ref)
    .put(AttributeNames.ActorSystem, system.classicSystem.name)
}
