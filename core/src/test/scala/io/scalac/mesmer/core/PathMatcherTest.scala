package io.scalac.mesmer.core

import io.scalac.mesmer.core.PathMatcher.{ Exact, Prefix }
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{ LoneElement, OptionValues }

class PathMatcherTest extends AnyFlatSpec with Matchers with LoneElement with OptionValues {

  "PathMatchers" should "match exact path" in {
    val matcher = PathMatcher.parse("/some/user", ()).value

    matcher should be(a[Exact[_]])
    matcher.matches("/some/user") should be(true)
    matcher.matches("/some/user/2") should be(false)
  }

  it should "match prefix" in {
    val matcher = PathMatcher.parse("/some/user/*", ()).value

    matcher should be(a[Prefix[_]])
    matcher.matches("/some/user") should be(true)
    matcher.matches("/some/user/2") should be(true)
    matcher.matches("/some/") should be(false)
  }

  it should "not be able to produce matcher if path doesn't start with slask" in {
    PathMatcher.parse("some/user/*", ()) should be(None)
  }

  it should "not parse if wildcard is in the middle" in {
    PathMatcher.parse("some/user*/", ()) should be(None)
  }

  it should "not parse if wildcard is not followed by slash" in {
    PathMatcher.parse("some/user*", ()) should be(None)
  }

  it should "match exact path with and without slash" in {
    val matcher = PathMatcher.parse("/some/user/", ()).value

    matcher should be(a[Exact[_]])
    matcher.matches("/some/user") should be(true)
    matcher.matches("/some/user/") should be(true)
  }

  it should "prefer exact over longer prefix" in {
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

  it should "prefer longer prefixes" in {
    val shortPrefix = PathMatcher.parse("/some/user/*", ()).value
    val longPrefix  = PathMatcher.parse("/some/user/more/specific/*", ()).value

    shortPrefix should be(a[Prefix[_]])
    longPrefix should be(a[Prefix[_]])

    longPrefix.compareTo(shortPrefix) should be > (0)
    shortPrefix.compareTo(longPrefix) should be < (0)
  }

}
