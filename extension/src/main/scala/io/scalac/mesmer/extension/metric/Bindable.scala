package io.scalac.mesmer.extension.metric

import io.scalac.mesmer.core.AttributesSerializable
import io.scalac.mesmer.core.model.RawAttributes

trait Unbind {
  private[scalac] def unbind(): Unit
}

trait RegisterRoot extends Unbind {
  private var _unbinds: List[Unbind] = Nil

  def registerUnbind(unbind: Unbind): Unit =
    _unbinds ::= unbind

  final def unbind(): Unit = _unbinds.foreach(_.unbind())
}

trait Bound extends Unbind

trait Bindable[L <: AttributesSerializable, +B <: Bound] extends (L => B) {
  final def apply(attributes: L): B = bind(attributes)
  def bind(attributes: L): B
}

trait EmptyBind[B <: Bound] extends Bindable[EmptyBind.EmptyAttributes, B] {
  final def bind(attributes: EmptyBind.EmptyAttributes): B = this.bind()
  def bind(): B
}

object EmptyBind {
  // no implementation of this is needed
  sealed trait EmptyAttributes extends AttributesSerializable {
    val serialize: RawAttributes = Seq.empty
  }
}
