package io.scalac.extension.metric

trait Asynchronized {
  def onCollect(cb: => Unit): Unit
}

object Asynchronized {

  trait CallbackListAsynchronized extends Asynchronized {
    import scala.collection.mutable

    private val callbacks = mutable.ListBuffer.empty[() => Unit]

    protected def callCallbacks(): Unit = callbacks.foreach(_.apply())

    def onCollect(cb: => Unit): Unit =
      callbacks += cb _
  }

}
