package io.scalac.mesmer.core

import scala.util.chaining._

import io.scalac.mesmer.core.model.Reporting

sealed trait ActorPathAttribute

final case class SomeActorPathAttribute(path: String) extends ActorPathAttribute
case object DisabledPath                              extends ActorPathAttribute

sealed trait ActorGrouping {
  protected def pathMatcher: PathMatcher

  def actorPathAttributeBuilder(actorPath: String): Option[ActorPathAttribute]
}

private case class ExactBaseGrouping(base: String, pathMatcher: PathMatcher) extends ActorGrouping {

  def actorPathAttributeBuilder(actorPath: String): Option[ActorPathAttribute] = if (pathMatcher.matches(actorPath)) {
    Some(SomeActorPathAttribute(base))
  } else None
}

private case class SingleVarGrouping(base: String, pathMatcher: PathMatcher) extends ActorGrouping {

  def actorPathAttributeBuilder(actorPath: String): Option[ActorPathAttribute] = {
    if (pathMatcher.matches(actorPath)) {
      val nextSegmentStart = actorPath.indexOf("/", base.length + 1)
      if (nextSegmentStart < 0) {
        Some(SomeActorPathAttribute(actorPath))
      } else
        Some(SomeActorPathAttribute(actorPath.substring(0, nextSegmentStart)))

    } else None
  }
}

private case class Identity(pathMatcher: PathMatcher) extends ActorGrouping {

  def actorPathAttributeBuilder(actorPath: String): Option[ActorPathAttribute] =
    if (pathMatcher.matches(actorPath)) {
      Some(SomeActorPathAttribute(actorPath))
    } else None
}

private case class Disabled(pathMatcher: PathMatcher) extends ActorGrouping {

  def actorPathAttributeBuilder(actorPath: String): Option[ActorPathAttribute] =
    if (pathMatcher.matches(actorPath)) {
      Some(DisabledPath)
    } else None
}

object ActorGrouping {

  implicit val ordering: Ordering[ActorGrouping] = Ordering.by(_.pathMatcher)

  sealed trait Error

  final case class InvalidRule(rule: String, message: String) extends Error

  def fromRule(rule: String, reporingType: Reporting): Either[Error, ActorGrouping] =
    rule match {
      case _ if rule.endsWith("/*") =>
        val matcherPath = rule.dropRight(2).pipe(toValidPath)

        reporingType match {
          case Reporting.Group =>
            PathMatcher
              .prefix(matcherPath, false)
              .map { matcher =>
                SingleVarGrouping(matcherPath, matcher)
              }
              .left
              .map { case PathMatcher.Error.MissingSlashError =>
                InvalidRule(rule, "Rule must start with a slash")
              }

          case Reporting.Instance =>
            PathMatcher
              .singleVariable(matcherPath)
              .map { matcher =>
                SingleVarGrouping(matcherPath, matcher)
              }
              .left
              .map { case PathMatcher.Error.MissingSlashError =>
                InvalidRule(rule, "Rule must start with a slash")
              }
          case Reporting.Disabled =>
            PathMatcher
              .singleVariable(matcherPath)
              .map { matcher =>
                Disabled(matcher)
              }
              .left
              .map { case PathMatcher.Error.MissingSlashError =>
                InvalidRule(rule, "Rule must start with a slash")
              }
        }

      case _ if rule.endsWith("/**") =>
        val matcherPath = rule.dropRight(3).pipe(toValidPath)

        reporingType match {
          case Reporting.Group =>
            PathMatcher
              .prefix(matcherPath, true)
              .map { matcher =>
                ExactBaseGrouping(matcherPath, matcher)
              }
              .left
              .map { case PathMatcher.Error.MissingSlashError =>
                InvalidRule(rule, "Rule must start with a slash")
              }
          case Reporting.Instance =>
            PathMatcher
              .prefix(matcherPath, false)
              .map { matcher =>
                Identity(matcher)
              }
              .left
              .map { case PathMatcher.Error.MissingSlashError =>
                InvalidRule(rule, "Rule must start with a slash")
              }
          case Reporting.Disabled =>
            PathMatcher
              .prefix(matcherPath, false)
              .map { matcher =>
                Disabled(matcher)
              }
              .left
              .map { case PathMatcher.Error.MissingSlashError =>
                InvalidRule(rule, "Rule must start with a slash")
              }
        }
      case x if !x.contains("*") =>
        val matcherPath = rule

        reporingType match {
          // TODO this should result in Left => there is not sense to group by one actor
          case Reporting.Group =>
            PathMatcher
              .prefix(matcherPath, true)
              .map { matcher =>
                ExactBaseGrouping(matcherPath, matcher)
              }
              .left
              .map { case PathMatcher.Error.MissingSlashError =>
                InvalidRule(rule, "Rule must start with a slash")
              }
          case Reporting.Instance =>
            PathMatcher
              .exact(matcherPath)
              .map { matcher =>
                ExactBaseGrouping(matcherPath, matcher)
              }
              .left
              .map { case PathMatcher.Error.MissingSlashError =>
                InvalidRule(rule, "Rule must start with a slash")
              }
          case Reporting.Disabled =>
            PathMatcher
              .exact(matcherPath)
              .map { matcher =>
                Disabled(matcher)
              }
              .left
              .map { case PathMatcher.Error.MissingSlashError =>
                InvalidRule(rule, "Rule must start with a slash")
              }
        }
      case _ => Left(InvalidRule(rule, "Rule cannot have wildcard in the middle"))
    }

  private def toValidPath(path: String): String = if (path.nonEmpty) path else "/"
}
