package io.scalac.mesmer.core

import org.scalatest.EitherValues
import org.scalatest.Inside
import org.scalatest.LoneElement
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import io.scalac.mesmer.core.PathMatcher.Exact
import io.scalac.mesmer.core.PathMatcher.Prefix

class PathMatcherTest extends AnyFlatSpec with Matchers with LoneElement with EitherValues with Inside {

  "PathMatcher" should "match exact path" in {
    val matcher = PathMatcher.parse("/some/user", ()).value

    inside(matcher) { case Exact(base, ()) =>
      base should be("/some/user/")
    }
    matcher.matches("/some/user") should be(true)
    matcher.matches("/some/user/") should be(true)
    matcher.matches("/some/user/2") should be(false)
    matcher.matches("/some/username") should be(false)
  }

  it should "match non recursive prefix" in {
    val matcher = PathMatcher.parse("/some/user/*", ()).value

    matcher should be(a[Prefix[_]])

    inside(matcher) { case Prefix(base, false, ()) =>
      base should be("/some/user/")
    }

    matcher.matches("/some/user/2") should be(true)
    matcher.matches("/some/user") should be(false)
    matcher.matches("/some/") should be(false)
    matcher.matches("/some/username") should be(false)
  }

  it should "match recursive prefix" in {
    val matcher = PathMatcher.parse("/some/user/**", ()).value

    matcher should be(a[Prefix[_]])

    inside(matcher) { case Prefix(base, true, ()) =>
      base should be("/some/user/")
    }

    matcher.matches("/some/user/2") should be(true)
    matcher.matches("/some/user/2/another") should be(true)
    matcher.matches("/some/user/2/another/more") should be(true)
    matcher.matches("/some/user") should be(false)
    matcher.matches("/some/username") should be(false)
    matcher.matches("/some/username/another") should be(false)
  }

  it should "match exact path with and without slash" in {
    val matcher = PathMatcher.parse("/some/user/", ()).value

    matcher should be(a[Exact[_]])
    matcher.matches("/some/user") should be(true)
    matcher.matches("/some/user/") should be(true)
  }

  "PathMatcher.parse" should "not be able to produce matcher if path doesn't start with slash" in {
    PathMatcher.parse("some/user/*", ()) should be(Left(PathMatcher.Error.MissingSlashError))
  }

  it should "not parse if wildcard is in the middle" in {
    PathMatcher.parse("/some/user*/", ()) should be(Left(PathMatcher.Error.InvalidWildcardError))
  }

  it should "not parse if wildcard is not followed by slash" in {
    PathMatcher.parse("/some/user*", ()) should be(Left(PathMatcher.Error.InvalidWildcardError))
  }

  "PathMatcher.compare" should "prefer exact over longer prefix" in {
    val exact  = PathMatcher.parse("/some/user/", ()).value
    val prefix = PathMatcher.parse("/some/user/more/specific/*", ()).value

    exact should be(a[Exact[_]])
    prefix should be(a[Prefix[_]])

    exact.compareTo(prefix) should be > (0)
    prefix.compareTo(exact) should be < (0)
  }

  it should "prefer longer exacts" in {
    val shortExact = PathMatcher.parse("/some/user/", ()).value
    val longExact  = PathMatcher.parse("/some/user/more/specific", ()).value

    shortExact should be(a[Exact[_]])
    longExact should be(a[Exact[_]])

    longExact.compareTo(shortExact) should be > (0)
    shortExact.compareTo(longExact) should be < (0)
  }

  it should "prefer longer prefixes for recursive" in {
    val shortPrefix = PathMatcher.parse("/some/user/**", ()).value
    val longPrefix  = PathMatcher.parse("/some/user/more/specific/**", ()).value

    shortPrefix should be(a[Prefix[_]])
    longPrefix should be(a[Prefix[_]])

    longPrefix.compareTo(shortPrefix) should be > (0)
    shortPrefix.compareTo(longPrefix) should be < (0)
  }

  it should "prefer longer prefixes for non recursive" in {
    val shortPrefix = PathMatcher.parse("/some/user/*", ()).value
    val longPrefix  = PathMatcher.parse("/some/user/more/specific/*", ()).value

    shortPrefix should be(a[Prefix[_]])
    longPrefix should be(a[Prefix[_]])

    longPrefix.compareTo(shortPrefix) should be(0)
    shortPrefix.compareTo(longPrefix) should be(0)
  }

  it should "prefer non recursive prefix over a recursive one with the same base" in {
    val nonRecursive = PathMatcher.parse("/some/user/*", ()).value
    val recursive    = PathMatcher.parse("/some/user/**", ()).value

    inside(nonRecursive) { case Prefix(_, false, _) => }
    inside(recursive) { case Prefix(_, true, _) => }

    nonRecursive.compareTo(recursive) should be > (0)
    recursive.compareTo(nonRecursive) should be < (0)
  }

  it should "prefer non recursive prefix over a recursive one when the former start with the latter" in {
    val nonRecursive = PathMatcher.parse("/some/user/session/*", ()).value
    val recursive    = PathMatcher.parse("/some/user/**", ()).value

    inside(nonRecursive) { case Prefix(_, false, _) => }
    inside(recursive) { case Prefix(_, true, _) => }

    nonRecursive.compareTo(recursive) should be > (0)
    recursive.compareTo(nonRecursive) should be < (0)
  }

  it should "prefer non recursive prefix over a recursive one when the latter start with the former" in {
    val nonRecursive = PathMatcher.parse("/some/user/*", ()).value
    val recursive    = PathMatcher.parse("/some/user/session/**", ()).value

    inside(nonRecursive) { case Prefix(_, false, _) => }
    inside(recursive) { case Prefix(_, true, _) => }

    nonRecursive.compareTo(recursive) should be > (0)
    recursive.compareTo(nonRecursive) should be < (0)
  }

}
