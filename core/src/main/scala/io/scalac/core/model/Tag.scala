package io.scalac.core.model

sealed trait Tag {
  def serialize: Seq[(String, String)]

  override lazy val toString = this.serialize.map {
    case (label, value) => s"$label -> $value"
  }.mkString("[", ",", "]")
}

object Tag {
  def stream: Tag = StreamTag


  private case object StreamTag extends Tag {
    override lazy val serialize: Seq[(String, String)] = Seq(("stream", "true"))
  }

  case class StageName(name: String) extends Tag {
    override def serialize: Seq[(String, String)] = Seq(("stage_name", name))
  }

  case class StreamName(name: String) extends Tag {
    override def serialize: Seq[(String, String)] = Seq(("stream_name", name))
  }
}
