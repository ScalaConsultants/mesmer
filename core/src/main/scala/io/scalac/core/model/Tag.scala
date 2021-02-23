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

  sealed trait StageName extends Tag {
    def name: String
  }

  object StageName {
    private[model] def serializeName(name: String): Seq[(String, String)] = Seq(("stage_name", name))

    def apply(name: String): StageName          = StageNameImpl(name)
    def apply(name: String, id: Int): StageName = StreamUniqueStageName(name, id)
  }

  private final case class StageNameImpl(override val name: String) extends StageName {
    override lazy val serialize: Seq[(String, String)] = StageName.serializeName(name)
  }

  private final case class StreamUniqueStageName(private val _name: String, id: Int) extends StageName {
    override lazy val name: String = s"${_name}/${id}"

    override lazy val serialize: Seq[(String, String)] = Seq("id" -> id.toString)
  }

  final case class StreamName(name: String) extends Tag {
    override def serialize: Seq[(String, String)] = StageName.serializeName(name) ++ Seq(("stream_name", name))
  }
}
