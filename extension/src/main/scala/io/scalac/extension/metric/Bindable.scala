package io.scalac.extension.metric

import io.scalac.core.LabelSerializable
import io.scalac.core.model.RawLabels

trait Unbind {
  def unbind(): Unit
}

trait Bound extends Unbind

trait Bindable[L <: LabelSerializable, +B <: Bound] extends (L => B) {
  final def apply(labels: L): B = bind(labels)
  def bind(labels: L): B
}

trait EmptyBind[B <: Bound] extends Bindable[EmptyBind.EmptyLabels, B] {
  final def bind(labels: EmptyBind.EmptyLabels): B = this.bind()
  def bind(): B
}

object EmptyBind {
  // no implementation of this is needed
  sealed trait EmptyLabels extends LabelSerializable {
    override val serialize: RawLabels = Seq.empty
  }
}
