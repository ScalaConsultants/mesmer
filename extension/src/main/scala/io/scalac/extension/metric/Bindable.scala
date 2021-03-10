package io.scalac.extension.metric

import io.scalac.core.model.LabelSerializer

trait Unbind {
  def unbind(): Unit
}

trait Bound extends Unbind

abstract class Bindable[L, +B <: Bound](implicit val labelSerializer: LabelSerializer[L]) extends (L => B) {
  final def apply(labels: L): B = bind(labels)
  def bind(labels: L): B
}

abstract class EmptyBind[B <: Bound] extends Bindable[Unit, B]()(EmptyBind.unitLabelSerializer) {
  final def bind(labels: Unit): B = this.bind()
  def bind(): B
}

object EmptyBind {
  implicit val unitLabelSerializer: LabelSerializer[Unit] = _ => Seq.empty
}
