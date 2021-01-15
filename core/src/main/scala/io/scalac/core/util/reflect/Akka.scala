package io.scalac.core.util.reflect

import akka.actor.ActorRef
import org.slf4j.LoggerFactory

import java.lang.invoke.MethodHandles._
import java.lang.invoke.MethodType._
import java.lang.invoke.{ MethodHandle, MethodHandles }
import scala.collection.{ immutable, mutable }
import scala.reflect.{ classTag, ClassTag }
import scala.util.Try

trait ClassProvider {

  protected val logger = LoggerFactory.getLogger(this.getClass)

  protected def registerRequired(fqcn: String): Unit
  def requiredClasses: Map[String, Class[_]]
  protected def findClass(fqcn: String): Option[Class[_]] = {
    logger.info(s"Searching for class ${fqcn}")
    val result = Try(Class.forName(fqcn)).toOption
    if (result.isEmpty)
      logger.error(s"Cannot locate ${fqcn}")
    result
  }

  trait Mirror[T] {
    type Public
    def mirroring: Class[_]

    def fqcn: String
  }

  object Mirror {
    type Aux[T, P] = Mirror[T] { type Public = P }
    def apply[T: Mirror]: Mirror[T] = implicitly[Mirror[T]]
  }

  trait Required[T] extends Mirror[T] {
    registerRequired(fqcn)
    override val mirroring: Class[_] = requiredClasses(fqcn) // this must guarantee not to throw
  }

  /**
   * Mix in this class if mirrored class has public base we can upcast to
   */
  trait PublicBase[M <: Mirror[_]] {
    type Base
    def tag: ClassTag[Base]
  }

  object PublicBase {
    type Aux[M <: Mirror[_], B] = PublicBase[M] { type Base = B }
  }

  // for not mirroring classes
  implicit def defaultCase[T: ClassTag]: Mirror[T] = new Mirror[T] {
    override val mirroring: Class[_] = classTag[T].runtimeClass

    override val fqcn: String = mirroring.getCanonicalName
  }

  implicit def publicBaseForIdentityMirror[T: ClassTag](implicit m: Mirror.Aux[T, T]): PublicBase.Aux[Mirror[T], T] =
    new PublicBase[Mirror[T]] {
      override type Base = T

      override def tag: ClassTag[T] = classTag[T]
    }
}

object ClassProvider {}

object AkkaMirrors extends ClassProvider {

  private[this] val lookup = MethodHandles.lookup()

  override protected def registerRequired(fqcn: String): Unit = _required.update(fqcn, findClass(fqcn).get)

  private[this] val _required: mutable.Map[String, Class[_]] = mutable.Map.empty

  override def requiredClasses: Map[String, Class[_]] = _required.toMap

  val actorRefWithCell: Option[Class[_]] = findClass("akka.actor.ActorRefWithCell")

  trait Cell {
    def childrenRefs: MirrorDelegation[Cell, ChildrenContainer] =
      MirrorDelegation(
        lookup.findVirtual(Mirror[Cell].mirroring, "childrenRefs", methodType(Mirror[ChildrenContainer].mirroring))
      )
  }

  object Cell extends Cell

  trait ChildrenContainer {

    /**
     * Unary operators expect this pointer passed to them
     * @return
     */
    def children: MirrorDelegation[ChildrenContainer, immutable.Iterable[ActorRef]] =
      MirrorDelegation[ChildrenContainer, immutable.Iterable[ActorRef]](
        lookup.findVirtual(Mirror[ChildrenContainer].mirroring, "children", methodType(classOf[immutable.Iterable[_]]))
      )
  }

  object ChildrenContainer extends ChildrenContainer

  trait AkkaRefWithCell {
    private val selfClass: Class[_] = Mirror[AkkaRefWithCell].mirroring
    val underlying: MirrorDelegation[AkkaRefWithCell, Cell] = {
      Mirror[AkkaRefWithCell]
      val handle =
        lookup.findVirtual(selfClass, "underlying", methodType(Mirror[Cell].mirroring))
      MirrorDelegation[AkkaRefWithCell, Cell](handle)
    }
  }

  object AkkaRefWithCell extends AkkaRefWithCell

  private implicit val cellMirror: Mirror[Cell] = new Required[Cell] {
    override lazy val fqcn: String = "akka.actor.Cell"
  }

  private implicit val akkaRefWithCellMirror: Mirror[AkkaRefWithCell] = new Required[AkkaRefWithCell] {
    override lazy val fqcn: String = "akka.actor.ActorRefWithCell"
  }

  private implicit val childrenContainerMirror: Mirror[ChildrenContainer] = new Required[ChildrenContainer] {
    override lazy val fqcn: String = "akka.actor.dungeon.ChildrenContainer"
  }

  case class MirrorDelegation[I, O](val handle: MethodHandle)(
    implicit val inputMirror: Mirror[I],
    implicit val outputMirror: Mirror[O]
  ) {
    def inputClass: Class[_]  = Mirror[I].mirroring
    def outputClass: Class[_] = Mirror[O].mirroring
  }

  implicit class AkkaMirrorDelegationOps[I, O](val value: MirrorDelegation[I, O])(implicit mo: Mirror[O]) {
    def andThen[O2](next: MirrorDelegation[O, O2]): MirrorDelegation[I, O2] = {
      import next.outputMirror
      import value.inputMirror

      val nextHandle = foldArguments(dropArguments(next.handle, 1, value.inputClass), value.handle)
      MirrorDelegation[I, O2](nextHandle)
    }

    def execute(input: Any)(implicit publicBase: PublicBase[Mirror[O]]): O = value.handle.invoke(input).asInstanceOf[O]
  }
}
