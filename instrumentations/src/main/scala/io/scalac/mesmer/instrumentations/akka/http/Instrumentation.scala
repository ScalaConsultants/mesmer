package io.scalac.mesmer.instrumentations.akka.http

import net.bytebuddy.asm.Advice
import net.bytebuddy.description.`type`.{ TypeDefinition, TypeDescription }
import net.bytebuddy.description.method.MethodDescription
import net.bytebuddy.dynamic.DynamicType
import net.bytebuddy.implementation.{ Implementation, MethodDelegation }
import net.bytebuddy.matcher.ElementMatcher

import scala.reflect.{ classTag, ClassTag }

object Instrumentation {
  type TypeDesc   = ElementMatcher.Junction[TypeDescription]
  type MethodDesc = ElementMatcher.Junction[MethodDescription]

  final case class InstrumentationDetails[S <: InstrumentationDetails.Status] private (
    name: String,
    tags: Set[String],
    isFQCN: Boolean
  )

  object InstrumentationDetails {
    sealed trait Status

    sealed trait FQCN extends Status

    sealed trait NonFQCN extends Status

    def fqcn(name: String, tags: Set[String]): InstrumentationDetails[FQCN] =
      InstrumentationDetails[FQCN](name, tags, isFQCN = true)
    def nonFQCN(name: String, tags: Set[String]): InstrumentationDetails[NonFQCN] =
      InstrumentationDetails[NonFQCN](name, tags, isFQCN = false)
  }

  final class Type(val name: InstrumentationDetails[_], val desc: TypeDesc) {
    def and(addDesc: TypeDesc): Type = new Type(name, desc.and(addDesc))
  }

  private type Builder = DynamicType.Builder[_]

  final class TypeInstrumentation private (
    val `type`: Type,
    val transformBuilder: Builder => Builder
  ) {

    def visit[T](method: MethodDesc)(implicit ct: ClassTag[T]): TypeInstrumentation =
      chain(_.visit(Advice.to(ct.runtimeClass).on(method)))

    def visit[A](advice: A, method: MethodDesc)(implicit isObject: A <:< Singleton): TypeInstrumentation =
      chain(_.visit(Advice.to(typeFromModule(advice.getClass)).on(method)))

    def intercept[A](advice: A, method: MethodDesc)(implicit isObject: A <:< Singleton): TypeInstrumentation =
      chain(_.method(method).intercept(Advice.to(typeFromModule(advice.getClass))))

    def intercept[T](method: MethodDesc)(implicit ct: ClassTag[T]): TypeInstrumentation =
      chain(_.method(method).intercept(Advice.to(ct.runtimeClass)))

    def intercept(method: MethodDesc, implementation: Implementation): TypeInstrumentation =
      chain(_.method(method).intercept(implementation))

    def delegate[T](method: MethodDesc)(implicit ct: ClassTag[T]): TypeInstrumentation =
      chain(_.method(method).intercept(MethodDelegation.to(ct.runtimeClass)))

    def defineField[T](name: String)(implicit ct: ClassTag[T]): TypeInstrumentation =
      chain(_.defineField(name, ct.runtimeClass))

    def defineMethod[T](name: String, result: TypeDefinition, impl: Implementation)(implicit
      ct: ClassTag[T]
    ): TypeInstrumentation =
      chain(_.defineMethod(name, result).intercept(impl))

    def implement[C: ClassTag](impl: Option[Implementation]): TypeInstrumentation =
      chain { builder =>
        val implemented = builder.implement(classTag[C].runtimeClass)
        impl.fold[Builder](implemented)(implemented.intercept)
      }

    private def typeFromModule(clazz: Class[_]): Class[_] = {
      val dollarFreeFQCN = clazz.getName.dropRight(1)
      Class.forName(dollarFreeFQCN, false, clazz.getClassLoader)
    }

    private def chain(that: Builder => Builder): TypeInstrumentation =
      new TypeInstrumentation(`type`, transformBuilder.andThen(that))

  }
}
