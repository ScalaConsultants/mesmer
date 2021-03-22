package io.scalac.extension.metric

import io.scalac.core.LabelSerializable
import io.scalac.core.model.RawLabels
import io.scalac.extension.upstream.opentelemetry.WrappedMetricObserver

trait Unbind {
  private[extension] def unbind(): Unit
}

trait UnbindRoot extends Unbind {
  private var _unbinds: List[Unbind] = Nil

  def registerUnbind(unbind: Unbind): Unit =
    _unbinds ::= unbind

  final def unbind(): Unit = _unbinds.foreach(_.unbind())
}

trait UnbindMany extends Unbind {
  private var _unbinds: List[Unbind] = Nil

  protected def pushUnbind(unbind: Unbind): Unit =
    _unbinds ::= unbind

  protected def registerObserver[T, L <: LabelSerializable](
    registration: (Unbind, WrappedMetricObserver[T, L])
  ): WrappedMetricObserver[T, L] = {
    pushUnbind(registration._1)
    registration._2
  }

  override def unbind(): Unit = _unbinds.foreach(_.unbind())
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
