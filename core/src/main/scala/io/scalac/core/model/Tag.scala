package io.scalac.core.model

sealed trait Tag extends Any {
  def serialize: Seq[(String, String)]

  override def toString =
    this.serialize.map { case (label, value) =>
      s"$label -> $value"
    }.mkString("[", ",", "]")
}

object Tag {
  val stream: Tag = StreamTag

  private case object StreamTag extends Tag {
    override lazy val serialize: Seq[(String, String)] = Seq(("stream", "true"))
  }

  sealed trait StageName extends Any with Tag {
    def name: String
    def nameOnly: StageName
  }

  object StageName {
    private[model] def serializeName(name: String): Seq[(String, String)] = Seq(("stream_stage", name))

    def apply(name: String): StageName = new StageNameImpl(name)

    def apply(name: String, id: Int): StreamUniqueStageName = StreamUniqueStageName(name, id)

    final case class StreamUniqueStageName(override val name: String, id: Int) extends StageName {

      private val streamUniqueName = s"$name/$id" // we use slash as this character will not appear in actor name

      override lazy val serialize: Seq[(String, String)] =
        StageName.serializeName(name) ++ Seq("stream_stage_unique_name" -> streamUniqueName)

      override def nameOnly: StageName = StageName(name)
    }

    final class StageNameImpl(val name: String) extends AnyVal with StageName {
      override def serialize: Seq[(String, String)] = StageName.serializeName(name)
      override def nameOnly: StageName              = this
    }
  }

  sealed trait SubStreamName extends Tag {
    def streamName: StreamName
    def subStreamId: String
  }

  object SubStreamName {
    def apply(streamName: String, island: String): SubStreamName = StreamNameWithIsland(streamName, island)

    private final case class StreamNameWithIsland(_streamName: String, islandId: String) extends SubStreamName {
      override val streamName: StreamName = StreamName(_streamName)
      override val subStreamId: String    = islandId
      override lazy val serialize: Seq[(String, String)] =
        Seq(("stream_name_with_island", s"$streamName-$subStreamId")) ++ streamName.serialize
    }
  }

  sealed trait StreamName extends Any with Tag {
    def name: String
  }

  object StreamName {
    def apply(name: String): StreamName = new StreamNameImpl(name)

    final class StreamNameImpl(val name: String) extends AnyVal with StreamName {
      override def serialize: Seq[(String, String)] = Seq("stream_name" -> name)
    }
  }

}
