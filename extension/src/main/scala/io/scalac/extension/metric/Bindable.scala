package io.scalac.extension.metric

trait Bindable[L] {

  type Bound

  def bind(lables: L): Bound
}
