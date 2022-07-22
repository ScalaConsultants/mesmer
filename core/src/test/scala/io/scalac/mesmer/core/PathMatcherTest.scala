package io.scalac.mesmer.core

import org.scalatest.EitherValues
import org.scalatest.Inside
import org.scalatest.LoneElement
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class PathMatcherTest extends AnyFlatSpec with Matchers with LoneElement with EitherValues with Inside {

  "PathMatcher" should "match exact path" in {
    val matcher = PathMatcher.exact("/some/user").value

    matcher.matches("/some/user") should be(true)
    matcher.matches("/some/user") should be(true)
    matcher.matches("/some/user/2") should not be (true)
    matcher.matches("/some/username") should not be (true)
  }

  it should "match single vairalbe" in {
    val matcher = PathMatcher.singleVariable("/some/user").value

    matcher.matches("/some/user/2") should be(true)
    matcher.matches("/some/user/a/b") should be(false)
    matcher.matches("/some/user") should be(false)
    matcher.matches("/some/") should be(false)
    matcher.matches("/some/username") should be(false)
  }

  it should "match prefix WITH exact" in {
    val matcher = PathMatcher.prefix("/some/user", true).value

    matcher.matches("/some/user/2") should be(true)
    matcher.matches("/some/user/2/another") should be(true)
    matcher.matches("/some/user/2/another/more") should be(true)
    matcher.matches("/some/user") should be(true)
    matcher.matches("/some/username") should be(false)
    matcher.matches("/some/username/another") should be(false)
  }

  it should "match prefix WITHOUT exact" in {
    val matcher = PathMatcher.prefix("/some/user", false).value

    matcher.matches("/some/user/2") should be(true)
    matcher.matches("/some/user/2/another") should be(true)
    matcher.matches("/some/user/2/another/more") should be(true)
    matcher.matches("/some/user") should be(false)
    matcher.matches("/some/username") should be(false)
    matcher.matches("/some/username/another") should be(false)
  }

  it should "match exact path with and without slash" in {
    val matcher = PathMatcher.exact("/some/user/").value

    matcher.matches("/some/user") should be(true)
    matcher.matches("/some/user/") should be(true)
  }

  "PathMatcher.parse" should "not be able to produce matcher if path doesn't start with slash" in {
    PathMatcher.exact("some/user") should be(Left(PathMatcher.Error.MissingSlashError))
    PathMatcher.singleVariable("some/user") should be(Left(PathMatcher.Error.MissingSlashError))
    PathMatcher.prefix("some/user", true) should be(Left(PathMatcher.Error.MissingSlashError))
  }
//
  it should "prefer longer exacts" in {
    val shortExact = PathMatcher.exact("/some/user").value
    val longExact  = PathMatcher.exact("/some/user/more/specific").value

    longExact.compareTo(shortExact) should be > (0)
    shortExact.compareTo(longExact) should be < (0)
  }
//
  it should "prefer longer prefixes" in {
    val shortPrefix = PathMatcher.prefix("/some/user", true).value
    val longPrefix  = PathMatcher.prefix("/some/user/more/specific", false).value

    longPrefix.compareTo(shortPrefix) should be > (0)
    shortPrefix.compareTo(longPrefix) should be < (0)
  }
//
//
  it should "prefer longer base " in {
    val exact  = PathMatcher.exact("/some/user").value
    val prefix = PathMatcher.prefix("/some/user/more/specific", true).value

    prefix.compareTo(exact) should be > (0)
    exact.compareTo(prefix) should be < (0)
  }

  it should "prefer prefer single variable over prefix without exact" in {
    val singleVar = PathMatcher.singleVariable("/some/user").value
    val prefix    = PathMatcher.prefix("/some/user", false).value

    singleVar.compareTo(prefix) should be > (0)
    prefix.compareTo(singleVar) should be < (0)
  }

  it should "prefer prefer prefix with exact over single variable" in {
    val singleVar = PathMatcher.singleVariable("/some/user").value
    val prefix    = PathMatcher.prefix("/some/user", true).value

    singleVar.compareTo(prefix) should be < (0)
    prefix.compareTo(singleVar) should be > (0)
  }

}
