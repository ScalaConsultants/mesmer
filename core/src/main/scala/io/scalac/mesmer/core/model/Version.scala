package io.scalac.mesmer.core.model

final case class Version(major: String, minor: String, patch: String)

object Version {
  def apply(version: String): Option[Version] =
    for {
      seq @ Array(major, minor, patch) <- Option(version.split('.')) if seq.length == 3
    } yield Version(major, minor, patch)

  def apply(major: Int, minor: Int, patch: Int): Version = Version(major.toString, minor.toString, patch.toString)
}
