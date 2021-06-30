package io.scalac.mesmer.agent.util

import net.bytebuddy.asm.Advice
import net.bytebuddy.description.`type`.TypeDefinition
import net.bytebuddy.description.`type`.TypeDescription
import net.bytebuddy.description.method.MethodDescription
import net.bytebuddy.dynamic.DynamicType
import net.bytebuddy.implementation.Implementation
import net.bytebuddy.implementation.MethodDelegation
import net.bytebuddy.matcher.ElementMatcher
import net.bytebuddy.matcher.{ElementMatchers => EM}

import scala.language.implicitConversions
import scala.reflect.ClassTag
import scala.reflect.classTag

import io.scalac.mesmer.agent.AgentInstrumentation
import io.scalac.mesmer.agent.util.i13n.InstrumentationDetails._

package object i13n {

  type TypeDesc   = ElementMatcher.Junction[TypeDescription]
  type MethodDesc = ElementMatcher.Junction[MethodDescription]

  final case class InstrumentationDetails[S <: Status] private (name: String, tags: Set[String], isFQCN: Boolean)

  object InstrumentationDetails {
    sealed trait Status

    sealed trait FQCN extends Status

    sealed trait NonFQCN extends Status

    def fqcn(name: String, tags: Set[String]): InstrumentationDetails[FQCN] =
      InstrumentationDetails[FQCN](name, tags, isFQCN = true)
    def nonFQCN(name: String, tags: Set[String]): InstrumentationDetails[NonFQCN] =
      InstrumentationDetails[NonFQCN](name, tags, isFQCN = false)
  }

  final class Type private[i13n] (private[i13n] val name: InstrumentationDetails[_], private[i13n] val desc: TypeDesc) {
    def and(addDesc: TypeDesc): Type = new Type(name, desc.and(addDesc))
  }

  // DSL

  def method(name: String): MethodDesc = EM.named[MethodDescription](name)

  def methods(first: MethodDesc, rest: MethodDesc*): MethodDesc = rest.fold(first)(_.or(_))

  val constructor: MethodDesc = EM.isConstructor

  def `type`(name: InstrumentationDetails[_], desc: TypeDesc): Type = new Type(name, desc)

  def hierarchy(details: InstrumentationDetails[FQCN]): Type =
    `type`(
      details,
      EM.hasSuperType[TypeDescription](EM.named[TypeDescription](details.name))
    )

  // wrappers

  private[i13n] type Builder = DynamicType.Builder[_]

  final class TypeInstrumentation private (
    private[i13n] val `type`: Type,
    private[i13n] val transformBuilder: Builder => Builder
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

  private[i13n] object TypeInstrumentation {
    private[i13n] def apply(target: Type): TypeInstrumentation = new TypeInstrumentation(target, identity)
  }

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
  implicit def fqcnDetailsToType(details: InstrumentationDetails[FQCN]): Type = `type`(details, details.name)
  implicit def methodNameToMethodDesc(methodName: String): MethodDesc         = method(methodName)
  implicit def classNameToTypeDesc(className: String): TypeDesc               = EM.named[TypeDescription](className)
  implicit def typeToAgentInstrumentation(typeInstrumentation: TypeInstrumentation): AgentInstrumentation =
    AgentInstrumentationFactory(typeInstrumentation, Seq.empty, false)

  implicit final class LoadingOps(private val value: TypeInstrumentation) extends AnyRef {
    def withLoad(fqcn: String, fqcns: String*): AgentInstrumentation =
      AgentInstrumentationFactory(value, fqcn +: fqcns, false)
    def deferred: AgentInstrumentation = AgentInstrumentationFactory(value, Seq.empty, true)
  }
}
