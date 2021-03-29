package io.scalac.agent.util

import scala.reflect.ClassTag
import scala.language.implicitConversions

import net.bytebuddy.description.method.MethodDescription
import net.bytebuddy.dynamic.DynamicType
import net.bytebuddy.matcher.ElementMatcher
import net.bytebuddy.matcher.{ ElementMatchers => EM }
import net.bytebuddy.asm.Advice
import net.bytebuddy.description.`type`.TypeDescription

package object i13n {

  final private[i13n] type TypeDesc   = ElementMatcher.Junction[TypeDescription]
  final private[i13n] type MethodDesc = ElementMatcher.Junction[MethodDescription]
  final class Type private[i13n] (private[i13n] val name: String, private[i13n] val desc: TypeDesc)

  // DSL

  final def visit[T](methodName: String)(implicit ct: ClassTag[T], builder: Builder): Unit =
    visit(method(methodName))

  final def visit[T](method: MethodDesc)(implicit ct: ClassTag[T], builder: Builder): Unit =
    builder.visit(method)

  final def intercept[T](methodName: String)(implicit ct: ClassTag[T], builder: Builder): Unit =
    intercept(method(methodName))

  final def intercept[T](method: MethodDesc)(implicit ct: ClassTag[T], builder: Builder): Unit =
    builder.intercept(method)

  final def defineField[T](name: String)(implicit ct: ClassTag[T], builder: Builder): Unit =
    builder.defineField(name)

  final def method(name: String): MethodDesc = EM.named[MethodDescription](name)

  final def constructor: MethodDesc = EM.isConstructor

  final def `type`(name: String): Type =
    new Type(name, EM.named[TypeDescription](name))

  final def hierarchy(name: String): Type =
    new Type(name, EM.hasSuperType[TypeDescription](EM.named[TypeDescription](name)))

  // wrappers

  final private[i13n] type WrappedBuilder = DynamicType.Builder[_]

  final class Builder(private[this] var wrapped: WrappedBuilder) {
    private[i13n] def get(): WrappedBuilder = wrapped

    private[i13n] def visit[T](method: MethodDesc)(implicit ct: ClassTag[T]): Unit =
      change(_.visit(Advice.to(ct.runtimeClass).on(method)))

    private[i13n] def intercept[T](method: MethodDesc)(implicit ct: ClassTag[T]): Unit =
      change(_.method(method).intercept(Advice.to(ct.runtimeClass)))

    private[i13n] def defineField[T](name: String)(implicit ct: ClassTag[T]): Unit =
      change(_.defineField(name, ct.runtimeClass))

    private[this] def change(nextValue: WrappedBuilder => WrappedBuilder): Unit =
      wrapped = nextValue(wrapped)
  }

  final private[i13n] object Builder {
    def apply(wrapped: WrappedBuilder)(block: Builder => Unit): WrappedBuilder = {
      val wrapper = new Builder(wrapped)
      block(wrapper)
      wrapper.get()
    }
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
    def takesArguments[A, B, C](implicit cta: ClassTag[A], ctb: ClassTag[B], ctc: ClassTag[C]): MethodDesc =
      methodDesc.and(EM.takesArguments(cta.runtimeClass, ctb.runtimeClass, ctc.runtimeClass))
    def takesArgument(index: Int, className: String): MethodDesc =
      methodDesc.and(EM.takesArgument(index, EM.named[TypeDescription](className)))
    def isOverriddenFrom(typeDesc: TypeDesc): MethodDesc =
      methodDesc.and(EM.isOverriddenFrom(typeDesc))
  }

}
