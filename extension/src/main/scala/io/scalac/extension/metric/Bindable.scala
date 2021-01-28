package io.scalac.extension.metric

trait Unbind {
  def unbind(): Unit
}

trait Bound extends Unbind

trait Bindable[L, +B <: Bound] extends (L => B) {
  final def apply(labels: L): B = bind(labels)
  def bind(labels: L): B
}
