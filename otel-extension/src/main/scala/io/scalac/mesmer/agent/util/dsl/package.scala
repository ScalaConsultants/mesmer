package io.scalac.mesmer.agent.util

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer
import net.bytebuddy.description.NamedElement
import net.bytebuddy.description.`type`.TypeDescription
import net.bytebuddy.description.method.MethodDescription
import net.bytebuddy.matcher.ElementMatcher
import net.bytebuddy.matcher.ElementMatchers

package object dsl {

  type MethodMatcher = ElementMatcher[MethodDescription]
  type TypeMatcher   = ElementMatcher[TypeDescription]

  // THIS IS A HACK BUT IT FORCES TYPE BOUND TO EVALUATE TO THE LOWEST POSSIBLE TYPE AND NOT NOTHING
  type Nothing0 = Nothing forSome { type T }

  object matchers {

    /*
      Maket ElementMacher type cast with variance?
     */
    def named[T >: Nothing0 <: NamedElement](name: String): ElementMatcher.Junction[T] = ElementMatchers.named[T](name)

    def hasSuperType[T >: Nothing0 <: TypeDescription](
      matcher: ElementMatcher[_ >: TypeDescription]
    ): ElementMatcher.Junction[T] =
      ElementMatchers.hasSuperType(matcher)

    def declaresMethod[T >: Nothing0 <: TypeDescription](
      method: ElementMatcher[_ >: MethodDescription]
    ): ElementMatcher.Junction[T] =
      ElementMatchers.declaresMethod[T](method)

    def isOverriddenFrom[T >: Nothing0 <: MethodDescription](
      base: ElementMatcher[_ >: TypeDescription]
    ): ElementMatcher.Junction[T] = ElementMatchers.isOverriddenFrom[T](base)

    def isConstructor[T >: Nothing0 <: MethodDescription]: ElementMatcher.Junction[T] = ElementMatchers.isConstructor[T]
    def takesArgument[T >: Nothing0 <: MethodDescription](
      pos: Int,
      typeDesc: ElementMatcher[_ >: TypeDescription]
    ): ElementMatcher.Junction[T] = ElementMatchers.takesArgument[T](pos, typeDesc)

  }

  /**
   * Factory method but with that will result in proper type inference - here we make more strict requirements for types
   * of matchers but is should not limit any capabilities of [[ElementMatchers]]
   */
  def typeInstrumentation(`type`: TypeMatcher)(method: MethodMatcher, advice: String): TypeInstrumentation =
    new TypeInstrumentation {
      val typeMatcher: ElementMatcher[TypeDescription] = `type`

      def transform(transformer: TypeTransformer): Unit = transformer.applyAdviceToMethod(method, advice)
    }
}
