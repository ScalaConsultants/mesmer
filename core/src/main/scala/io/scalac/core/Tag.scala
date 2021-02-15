package io.scalac.core

sealed trait Tag {
  def serialize: Array[String]

  override lazy val toString = this.serialize.mkString("[", ",", "]")
}

object Tag {
  def stream: Tag = StreamTag

  private object StreamTag extends Tag {
    override lazy val serialize: Array[String] = Array("stream", "true")
  }
}
