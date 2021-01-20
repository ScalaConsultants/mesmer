package io.scalac.extension.metric

trait Unbind {
  def unbind(): Unit
}

trait Bindable[L] {
  type Bound <: Unbind

  def bind(labels: L): Bound
}

object Bindable {
  type Aux[L, B0 <: Unbind] = Bindable[L] { type Bound = B0 }
}
