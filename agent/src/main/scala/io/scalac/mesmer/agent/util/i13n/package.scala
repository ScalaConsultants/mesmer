package io.scalac.mesmer.agent.util

import io.scalac.mesmer.agent.AgentInstrumentation
import io.scalac.mesmer.core.model.SupportedModules
import net.bytebuddy.asm.Advice
import net.bytebuddy.description.`type`.TypeDescription
import net.bytebuddy.description.method.MethodDescription
import net.bytebuddy.dynamic.DynamicType
import net.bytebuddy.implementation.{ Implementation, MethodDelegation }
import net.bytebuddy.matcher.{ ElementMatcher, ElementMatchers => EM }

import scala.language.implicitConversions
import scala.reflect.{ classTag, ClassTag }

package object i13n {

  final private[i13n] type TypeDesc   = ElementMatcher.Junction[TypeDescription]
  final private[i13n] type MethodDesc = ElementMatcher.Junction[MethodDescription]

  final class Type private[i13n] (private[i13n] val name: String, private[i13n] val desc: TypeDesc)

  // DSL

  def method(name: String): MethodDesc = EM.named[MethodDescription](name)

  def methods(first: MethodDesc, rest: MethodDesc*): MethodDesc = rest.fold(first)(_.or(_))

  val constructor: MethodDesc = EM.isConstructor

  def `type`(name: String): Type =
    `type`(name, EM.named[TypeDescription](name))

  def `type`(name: String, desc: TypeDesc): Type = new Type(name, desc)

  def hierarchy(name: String): Type =
    `type`(
      name,
      EM.hasSuperType[TypeDescription](EM.named[TypeDescription](name))
        .and(EM.not[TypeDescription](EM.isAbstract[TypeDescription]))
    )

  def superTypes(name: String, desc: TypeDescription): Type = `type`(name, EM.isSuperTypeOf(desc))

  // wrappers

  private[i13n] type Builder = DynamicType.Builder[_]

  final class TypeInstrumentation private (
    private[i13n] val target: TypeTarget,
    private[i13n] val transformBuilder: Builder => Builder
  ) {

    def visit[T](method: MethodDesc)(implicit ct: ClassTag[T]): TypeInstrumentation =
      chain(_.visit(Advice.to(ct.runtimeClass).on(method)))

    def intercept[T](method: MethodDesc)(implicit ct: ClassTag[T]): TypeInstrumentation =
      chain(_.method(method).intercept(Advice.to(ct.runtimeClass)))

    def intercept(method: MethodDesc, implementation: Implementation): TypeInstrumentation =
      chain(_.method(method).intercept(implementation))

    def delegate[T](method: MethodDesc)(implicit ct: ClassTag[T]): TypeInstrumentation =
      chain(_.method(method).intercept(MethodDelegation.to(ct.runtimeClass)))

    def defineField[T](name: String)(implicit ct: ClassTag[T]): TypeInstrumentation =
      chain(_.defineField(name, ct.runtimeClass))

    def implement[C: ClassTag](impl: Option[Implementation]): TypeInstrumentation =
      chain { builder =>
        val implemented = builder.implement(classTag[C].runtimeClass)
        impl.fold[Builder](implemented)(implemented.intercept)
      }

    private def chain(that: Builder => Builder): TypeInstrumentation =
      new TypeInstrumentation(target, transformBuilder.andThen(that))

  }

  private[i13n] object TypeInstrumentation {
    private[i13n] def apply(target: TypeTarget): TypeInstrumentation = new TypeInstrumentation(target, identity)
  }

  final private[i13n] case class TypeTarget(tpe: Type, modules: SupportedModules)

  // extensions

  sealed trait TypeDescLike[T] extends Any {
    // This trait intents to reuse all the transformations available both TypeDesc and Type
    def overrides(methodDesc: MethodDesc): T = declares(methodDesc.isOverriddenFrom(typeDesc))
    def declares(methodDesc: MethodDesc): T  = and(EM.declaresMethod(methodDesc))
    def concreteOnly: T                      = and(EM.not[TypeDescription](EM.isAbstract[TypeDescription]))
    protected def typeDesc: TypeDesc
    protected def and(that: TypeDesc): T
  }

  final implicit class TypeOps(val tpe: Type) extends AnyVal with TypeDescLike[Type] {
    protected def and(that: TypeDesc): Type = new Type(tpe.name, tpe.desc.and(that))
    protected def typeDesc: TypeDesc        = tpe.desc
  }

  final implicit class TypeDescOps(val typeDesc: TypeDesc) extends AnyVal with TypeDescLike[TypeDesc] {
    protected def and(that: TypeDesc): TypeDesc = typeDesc.and(that)
  }

  final implicit class MethodDescOps(private val methodDesc: MethodDesc) extends AnyVal {
    def takesArguments(n: Int): MethodDesc =
      methodDesc.and(EM.takesArguments(n))
    def takesArguments[A, B](implicit cta: ClassTag[A], ctb: ClassTag[B]): MethodDesc =
      takesArguments(cta.runtimeClass, ctb.runtimeClass)
    def takesArguments[A, B, C](implicit cta: ClassTag[A], ctb: ClassTag[B], ctc: ClassTag[C]): MethodDesc =
      takesArguments(cta.runtimeClass, ctb.runtimeClass, ctc.runtimeClass)
    private def takesArguments(classes: Class[_]*): MethodDesc =
      methodDesc.and(EM.takesArguments(classes: _*))
    def takesArgument(index: Int, className: String): MethodDesc =
      methodDesc.and(EM.takesArgument(index, EM.named[TypeDescription](className)))
    def isOverriddenFrom(typeDesc: TypeDesc): MethodDesc =
      methodDesc.and(EM.isOverriddenFrom(typeDesc))
  }

  // implicit conversion
  implicit def methodNameToMethodDesc(methodName: String): MethodDesc = method(methodName)
  implicit def typeNameToType(typeName: String): Type                 = `type`(typeName)
  implicit def typeToAgentInstrumentation(typeInstrumentation: TypeInstrumentation): AgentInstrumentation =
    AgentInstrumentationFactory(typeInstrumentation)
}
