package io.scalac.subst;

import akka.actor.ClassicActorContextProvider;
import akka.actor.typed.scaladsl.AbstractBehavior;

public class Dummy {

  public static ClassicActorContextProvider dummyContext(AbstractBehavior<?> self) {
    throw new IllegalStateException(
        "Something went wrong - this call should have been substituted by a real method");
  }
}
