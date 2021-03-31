package io.scalac.agent.util

import scala.reflect.ClassTag
import scala.language.implicitConversions

import net.bytebuddy.description.method.MethodDescription
import net.bytebuddy.dynamic.DynamicType
import net.bytebuddy.matcher.ElementMatcher
import net.bytebuddy.matcher.{ ElementMatchers => EM }
import net.bytebuddy.asm.Advice
import net.bytebuddy.description.`type`.TypeDescription
import net.bytebuddy.implementation.MethodDelegation

package object i13n {

  final private[i13n] type TypeDesc   = ElementMatcher.Junction[TypeDescription]
  final private[i13n] type MethodDesc = ElementMatcher.Junction[MethodDescription]
  final class Type private[i13n] (private[i13n] val name: String, private[i13n] val desc: TypeDesc)

  // DSL

  final def method(name: String): MethodDesc = EM.named[MethodDescription](name)

  final def methods(first: MethodDesc, rest: MethodDesc*): MethodDesc = rest.fold(first)(_.or(_))

  final def constructor: MethodDesc = EM.isConstructor

  final def `type`(name: String): Type =
    new Type(name, EM.named[TypeDescription](name))

  final def hierarchy(name: String): Type =
    new Type(name, EM.hasSuperType[TypeDescription](EM.named[TypeDescription](name)))

  // wrappers

  final private[i13n] type Builder = DynamicType.Builder[_]

  final class BuilderTransformer private (transform: Builder => Builder) extends (Builder => Builder) {

    override def apply(v1: Builder): Builder = transform(v1)

    def visit[T](method: MethodDesc)(implicit ct: ClassTag[T]): BuilderTransformer =
      chain(_.visit(Advice.to(ct.runtimeClass).on(method)))

    def intercept[T](method: MethodDesc)(implicit ct: ClassTag[T]): BuilderTransformer =
      chain(_.method(method).intercept(Advice.to(ct.runtimeClass)))

    def delegate[T](method: MethodDesc)(implicit ct: ClassTag[T]): BuilderTransformer =
      chain(_.method(method).intercept(MethodDelegation.to(ct.runtimeClass)))

    def defineField[T](name: String)(implicit ct: ClassTag[T]): BuilderTransformer =
      chain(_.defineField(name, ct.runtimeClass))

    private def chain(that: Builder => Builder): BuilderTransformer =
      new BuilderTransformer(transform.andThen(that))

  }

  private[i13n] final object BuilderTransformer {
    private[i13n] val unit: BuilderTransformer = new BuilderTransformer(identity)
  }

  // extensions

  sealed trait TypeDescLike[T] extends Any {
    // This trait intents to reuse all the transformations available both TypeDesc and Type
    def overrides(methodDesc: MethodDesc): T = declares(methodDesc.isOverriddenFrom(typeDesc))
    def declares(methodDesc: MethodDesc): T  = and(EM.declaresMethod(methodDesc))
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

  final implicit class MethodDescOps(val methodDesc: MethodDesc) extends AnyVal {
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
  final implicit def methodNameToMethodDesc(methodName: String): MethodDesc = method(methodName)
  final implicit def typeNameToType(typeName: String): Type                 = `type`(typeName)
}
