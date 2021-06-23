package io.scalac.mesmer.agent.akka.actor.impl

import akka.actor.ActorContext
import akka.actor.typed.TypedActorContext

import java.lang.invoke.MethodHandles

object ClassicActorContextProviderOps {

  private lazy val classicActorContextGetter = {
    val method = Class
      .forName("akka.actor.ClassicActorContextProvider")
      .getDeclaredMethod("classicActorContext")
    method.setAccessible(true)
    MethodHandles.lookup().unreflect(method)
  }

  @inline def classicActorContext(context: TypedActorContext[_]): ActorContext =
    classicActorContextGetter.invoke(context)

}
