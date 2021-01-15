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
  protected def findClass(fqcn: String): Option[Class[_]] = Try(Class.forName(fqcn)).toOption

  /**
   * Mirrors provide information which private class is mirrored by type [[T]]. This allows us to
   * use types instead of values and create typesafe abstraction on bindings between methods
   *
   * @tparam T class that mirrors other class. Mirror mean that it should provide methods that
   *           that resembles original ones but with (probably) changed signature
   */
  trait Mirror[T] {

    /**
     *
     * @return Reference to class that [[T]] mirrors
     */
    def mirroring: Class[_]

    /**
     * Fully qualified class name of mirrored class
     * @return
     */
    def fqcn: String
  }

  object Mirror {
    type Aux[T, P] = Mirror[T] { type Public = P }
    def apply[T: Mirror]: Mirror[T] = implicitly[Mirror[T]]
  }

  /**
   * Mixing trait that automatically registers mirrored class and put it in cache
   * @tparam T class that mirrors other class. Mirror mean that it should provide methods that
   *           that resembles original ones but with (probably) changed signature
   */
  trait Required[T] extends Mirror[T] {
    registerRequired(fqcn)
    override val mirroring: Class[_] = requiredClasses(fqcn) // this must guarantee not to throw
  }

  /**
   * All classes are treated as mirrors of themselves until specified otherwise (with implicit val)
   * @tparam T
   * @return
   */
  implicit def indentity[T: ClassTag]: Mirror[T] = new Mirror[T] {
    override val mirroring: Class[_] = classTag[T].runtimeClass

    override val fqcn: String = mirroring.getCanonicalName
  }
}

object ClassProvider {}

object AkkaMirrors extends ClassProvider {

  private[this] val lookup = MethodHandles.lookup()

  override protected def registerRequired(fqcn: String): Unit = _required.update(fqcn, findClass(fqcn).get)

  private[this] val _required: mutable.Map[String, Class[_]] = mutable.Map.empty

  override def requiredClasses: Map[String, Class[_]] = _required.toMap

  val actorRefWithCell: Option[Class[_]] = findClass("akka.actor.ActorRefWithCell")

  /**
   * Type mirroring [[akka.actor.Cell]]
   */
  sealed trait Cell {

    /**
     * @return Delegation to another mirror
     */
    def childrenRefs: MirrorDelegation[Cell, ChildrenContainer] =
      new MirrorDelegation(
        lookup.findVirtual(Mirror[Cell].mirroring, "childrenRefs", methodType(Mirror[ChildrenContainer].mirroring))
      )
  }

  /**
   * This will be only instance of [[Cell]] trait
   */
  object Cell extends Cell

  /**
   * Type mirroring [[akka.actor.dungeon.ChildrenContainer]]
   */
  trait ChildrenContainer {

    /**
     * Unary operators expect this pointer passed to them
     * @return
     */
    def children: MirrorDelegation[ChildrenContainer, immutable.Iterable[ActorRef]] =
      new MirrorDelegation[ChildrenContainer, immutable.Iterable[ActorRef]](
        lookup.findVirtual(Mirror[ChildrenContainer].mirroring, "children", methodType(classOf[immutable.Iterable[_]]))
      )
  }

  object ChildrenContainer extends ChildrenContainer

  /**
   * Type mirroring [[akka.actor.ActorRefWithCell]]
   */
  trait AkkaRefWithCell {
    private val selfClass: Class[_] = Mirror[AkkaRefWithCell].mirroring
    val underlying: MirrorDelegation[AkkaRefWithCell, Cell] = {
      Mirror[AkkaRefWithCell]
      val handle =
        lookup.findVirtual(selfClass, "underlying", methodType(Mirror[Cell].mirroring))
      new MirrorDelegation[AkkaRefWithCell, Cell](handle)
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
  //TODO see if all elements are necessary for this class
  /**
   *
   * @param handle method handle that is responsible for function I => O
   * @param inputMirror
   * @param outputMirror
   * @tparam I
   * @tparam O
   */
  class MirrorDelegation[I, O](val handle: MethodHandle)(
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
      new MirrorDelegation[I, O2](nextHandle)
    }
  }
}
