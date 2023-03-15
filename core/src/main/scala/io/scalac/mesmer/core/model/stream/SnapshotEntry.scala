package io.scalac.mesmer.core.model.stream

final case class SnapshotEntry(stage: StageInfo, data: Option[StageData])
