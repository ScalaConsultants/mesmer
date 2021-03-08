package io.scalac.core.invoke
import java.lang.invoke.MethodHandles

/**
 * Marking trait that class uses java method handles inside. Gives access to lookup object
 */
private[scalac] trait Lookup {

  protected val lookup = MethodHandles.lookup()

}
