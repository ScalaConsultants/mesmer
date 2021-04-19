package io.scalac.core.invoke
import java.lang.invoke.MethodHandles

/**
 * Marking trait that class uses java method handles inside. Gives access to lookup object
 */
trait Lookup {

  protected val lookup: MethodHandles.Lookup = MethodHandles.lookup()

}
