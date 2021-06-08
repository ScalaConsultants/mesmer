package io.scalac.mesmer.core.model

import io.scalac.mesmer.core.model.Tag.StreamName.StreamNameLabel

sealed trait Tag extends Any {
  def serialize: Seq[(String, String)]

  override def toString: String =
    this.serialize.map { case (label, value) =>
      s"$label -> $value"
    }.mkString("[", ", ", "]")
}

object Tag {
  val stream: Tag     = StreamTag
  val terminated: Tag = TerminatedTag
  val all: Tag        = All

  private case object All extends Tag {
    override def serialize: Seq[(String, String)] = Seq.empty
  }

  private case object StreamTag extends Tag {
    lazy val serialize: Seq[(String, String)] = Seq(("stream", "true"))
  }

  private case object TerminatedTag extends Tag {
    lazy val serialize: Seq[(String, String)] = Seq.empty
  }

  sealed trait StageName extends Any with Tag {
    def name: String
    def nameOnly: StageName
  }

  object StageName {
    private[model] def serializeName(name: String): Seq[(String, String)] = Seq(("stream_stage", name))

    def apply(name: String): StageName = new StageNameImpl(name)

    def apply(name: String, id: Int): StreamUniqueStageName = StreamUniqueStageName(name, id)

    final case class StreamUniqueStageName(val name: String, id: Int) extends StageName {

      private val streamUniqueName = s"$name/$id" // we use slash as this character will not appear in actor name

      lazy val serialize: Seq[(String, String)] =
        StageName.serializeName(name) ++ Seq("stream_stage_unique_name" -> streamUniqueName)

      def nameOnly: StageName = StageName(name)
    }

    final class StageNameImpl(val name: String) extends AnyVal with StageName {
      def serialize: Seq[(String, String)] = StageName.serializeName(name)
      def nameOnly: StageName              = this
    }
  }

  sealed trait SubStreamName extends Tag {
    def streamName: StreamName
    def subStreamId: String
  }

  object SubStreamName {
    def apply(streamName: String, island: String): SubStreamName = StreamNameWithIsland(streamName, island)

    private final case class StreamNameWithIsland(_streamName: String, islandId: String) extends SubStreamName {
      val streamName: StreamName = StreamName(_streamName)
      val subStreamId: String    = islandId
      lazy val serialize: Seq[(String, String)] =
        Seq(("stream_name_with_island", s"${streamName.name}-$subStreamId")) ++ streamName.serialize
    }
  }

  sealed trait StreamName extends Any with Tag {
    def name: String

    def serialize: Seq[(String, String)] = Seq(StreamNameLabel -> name)
  }

  object StreamName {
    def apply(name: String): StreamName = new StreamNameImpl(name)

    def apply(matName: StreamName, terminalStage: StageName): StreamName =
      TerminalOperatorStreamName(matName, terminalStage)

    private final val StreamNameLabel = "stream_name"

    final class StreamNameImpl(val name: String) extends AnyVal with StreamName

    private final case class TerminalOperatorStreamName(materializationName: StreamName, terminalStageName: StageName)
        extends StreamName {
      val name: String = terminalStageName.name

      override def serialize: Seq[(String, String)] =
        super.serialize ++ Seq("materialization_name" -> materializationName.name)
    }

  }

}
