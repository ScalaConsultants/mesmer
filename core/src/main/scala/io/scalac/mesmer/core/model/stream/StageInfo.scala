package io.scalac.mesmer.core.model.stream

import io.scalac.mesmer.core.model.Tag.StageName.StreamUniqueStageName
import io.scalac.mesmer.core.model.Tag.SubStreamName

final case class StageInfo(
  id: Int,
  stageName: StreamUniqueStageName,
  subStreamName: SubStreamName,
  terminal: Boolean = false
)
