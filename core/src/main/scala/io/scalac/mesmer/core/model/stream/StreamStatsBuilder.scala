package io.scalac.mesmer.core.model.stream

import io.scalac.mesmer.core.model.Tag.StageName
import io.scalac.mesmer.core.model.Tag.StreamName

final class StreamStatsBuilder(val materializationName: StreamName) {
  private[this] var terminalName: Option[StageName] = None
  private[this] var processedMessages: Long         = 0
  private[this] var actors: Int                     = 0
  private[this] var stages: Int                     = 0

  def incActors(): this.type = {
    actors += 1
    this
  }

  def incStage(): this.type = {
    stages += 1
    this
  }

  def addStages(num: Int): this.type = {
    stages += 1
    this
  }

  def terminalName(stageName: StageName): this.type =
    if (terminalName.isEmpty) {
      this.terminalName = Some(stageName)
      this
    } else throw new IllegalStateException("Terminal name can be set once")

  def processedMessages(value: Long): this.type = {
    processedMessages = value
    this
  }

  def build: StreamStats = StreamStats(
    terminalName.fold(materializationName)(stage => StreamName(materializationName, stage)),
    actors,
    stages,
    processedMessages
  )

}
