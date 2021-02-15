package io.scalac.core

sealed trait Tag {
  def serialize: Seq[(String, String)]

  override lazy val toString = this.serialize.map {
    case (label, value) => s"$label -> $value"
  }.mkString("[", ",", "]")
}

object Tag {
  def stream: Tag = StreamTag

  private object StreamTag extends Tag {
    override lazy val serialize: Seq[(String, String)] = Seq(("stream", "true"))
  }
}
