package io.scalac.extension.metric

trait Unbind {
  def unbind(): Unit
}

trait Bound extends Unbind

//trait LabelSerializable extends Any {
//  def serialize: RawLabels
//}
//
//object EmptyLabels extends LabelSerializable {
//  override val serialize: RawLabels = Seq.empty
//}

trait Bindable[-L, +B <: Bound] extends (L => B) {
  final def apply(labels: L): B = bind(labels)
  def bind(labels: L): B
}

trait EmptyBind[B <: Bound] extends Bindable[Unit, B] {
  final def bind(labels: Unit): B = this.bind()
  def bind(): B
}
