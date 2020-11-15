package io.scalac.extension.metric

trait Bindable[T] {

  type Bound

  def bind(node: T): Bound
}
