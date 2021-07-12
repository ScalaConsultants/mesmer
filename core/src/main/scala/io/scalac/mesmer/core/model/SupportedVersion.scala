package io.scalac.mesmer.core.model

//TODO add descriptive toString
sealed trait SupportedVersion {
  import SupportedVersion._

  def &&(other: SupportedVersion): SupportedVersion = and(other)

  def and(other: SupportedVersion): SupportedVersion = And(this, other)

  def ||(other: SupportedVersion): SupportedVersion = or(other)

  def or(other: SupportedVersion): SupportedVersion = other match {
    case AnyVersion => AnyVersion
    case _          => Or(this, other)
  }

  final def supports(version: Version): Boolean = this match {
    case AnyVersion         => true
    case Selected(versions) => versions.contains(version)
    case WithMajor(major)   => version.major == major
    case WithMinor(minor)   => version.minor == minor
    case WithPatch(patch)   => version.patch == patch
    case Not(supported)     => !supported.supports(version)
    case And(left, right)   => left.supports(version) && right.supports(version)
    case Or(left, right)    => left.supports(version) || right.supports(version)
  }

  def unary_! : SupportedVersion = Not(this)
}

object SupportedVersion {
  private final case class And(left: SupportedVersion, right: SupportedVersion) extends SupportedVersion
  private final case class Or(left: SupportedVersion, right: SupportedVersion)  extends SupportedVersion

  private case object AnyVersion extends SupportedVersion {
    override def ||(other: SupportedVersion): SupportedVersion = this // short circuit
  }

  private final case class Selected(version: Seq[Version]) extends SupportedVersion

  private final case class WithMajor(major: String) extends SupportedVersion

  private final case class WithMinor(minor: String) extends SupportedVersion

  private final case class WithPatch(patch: String) extends SupportedVersion

  private final case class Not(supportedVersion: SupportedVersion) extends SupportedVersion

  def apply(versions: Seq[Version]): SupportedVersion        = Selected(versions)
  def apply(head: Version, tail: Version*): SupportedVersion = apply(head +: tail)
  def any: SupportedVersion                                  = AnyVersion
  def none: SupportedVersion                                 = Not(any)
  def minors(head: String, tail: String*): SupportedVersion = tail.foldLeft[SupportedVersion](WithMinor(head)) {
    case (acc, minor) => Or(acc, WithMinor(minor))
  }
  def minors(values: Seq[String]): SupportedVersion =
    values match {
      case x :: xs => minors(x, xs: _*)
      case _       => none
    }

  def majors(values: Seq[String]): SupportedVersion =
    values match {
      case x :: xs => majors(x, xs: _*)
      case _       => none
    }

  def majors(head: String, tail: String*): SupportedVersion = tail.foldLeft[SupportedVersion](WithMajor(head)) {
    case (acc, minor) => Or(acc, WithMajor(minor))
  }

  def patches(values: Seq[String]): SupportedVersion =
    values match {
      case x :: xs => patches(x, xs: _*)
      case _       => none
    }

  def patches(head: String, tail: String*): SupportedVersion = tail.foldLeft[SupportedVersion](WithPatch(head)) {
    case (acc, patch) => Or(acc, WithPatch(patch))
  }
}
