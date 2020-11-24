package io.scalac.extension.metric

trait Bindable[L] {

  type Bound

  def bind(lables: L): Bound
}

object Bindable {
  type Aux[L, B0] = Bindable[L] { type Bound = B0 }
}
