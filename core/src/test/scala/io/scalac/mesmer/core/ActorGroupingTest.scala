package io.scalac.mesmer.core

import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import io.scalac.mesmer.core.model.Reporting

class ActorGroupingTest extends AnyFlatSpec with Matchers with EitherValues {

  "ActorGrouping without wildcard" should "create exactGrouping with grouping reporting" in {

    val grouping = ActorGrouping.fromRule("/user/test", Reporting.group).value

    grouping.actorPathAttributeBuilder("/user/test/a") should be(Some(SomeActorPathAttribute("/user/test")))
    grouping.actorPathAttributeBuilder("/user/test/a/b/c") should be(Some(SomeActorPathAttribute("/user/test")))
    grouping.actorPathAttributeBuilder("/user/test") should be(Some(SomeActorPathAttribute("/user/test")))
    grouping.actorPathAttributeBuilder("/user/another") should be(None)
  }

  it should "create exactGrouping with instance reporting" in {

    val grouping = ActorGrouping.fromRule("/user/test", Reporting.instance).value

    grouping.actorPathAttributeBuilder("/user/test/a") should be(None)
    grouping.actorPathAttributeBuilder("/user/test/a/b/c") should be(None)
    grouping.actorPathAttributeBuilder("/user/test") should be(Some(SomeActorPathAttribute("/user/test")))
    grouping.actorPathAttributeBuilder("/user/another") should be(None)
  }

  it should "create disabled with disabled grouping" in {

    val grouping = ActorGrouping.fromRule("/user/test", Reporting.disabled).value

    grouping.actorPathAttributeBuilder("/user/test/a") should be(None)
    grouping.actorPathAttributeBuilder("/user/test/a/b/c") should be(None)
    grouping.actorPathAttributeBuilder("/user/test") should be(Some(DisabledPath))
    grouping.actorPathAttributeBuilder("/user/another") should be(None)
  }

  "ActorGrouping with /*" should "create single var grouping for grouping reporting " in {

    val grouping = ActorGrouping.fromRule("/user/test/*", Reporting.group).value

    grouping.actorPathAttributeBuilder("/user/test/a") should be(Some(SomeActorPathAttribute("/user/test/a")))
    grouping.actorPathAttributeBuilder("/user/test/a/b/c") should be(Some(SomeActorPathAttribute("/user/test/a")))
    grouping.actorPathAttributeBuilder("/user/test/b/b/c") should be(Some(SomeActorPathAttribute("/user/test/b")))
    grouping.actorPathAttributeBuilder("/user/test") should be(None)
    grouping.actorPathAttributeBuilder("/user/another") should be(None)
  }

  it should "create single var grouping with single var matcher for instance reporting" in {
    val grouping = ActorGrouping.fromRule("/user/test/*", Reporting.instance).value

    grouping.actorPathAttributeBuilder("/user/test/a") should be(Some(SomeActorPathAttribute("/user/test/a")))
    grouping.actorPathAttributeBuilder("/user/test/b") should be(Some(SomeActorPathAttribute("/user/test/b")))
    grouping.actorPathAttributeBuilder("/user/test/a/b/c") should be(None)
    grouping.actorPathAttributeBuilder("/user/test/b/b/c") should be(None)
    grouping.actorPathAttributeBuilder("/user/test") should be(None)
    grouping.actorPathAttributeBuilder("/user/another") should be(None)
  }

  it should "create disabled with single var matcher for instance reporting" in {
    val grouping = ActorGrouping.fromRule("/user/test/*", Reporting.disabled).value

    grouping.actorPathAttributeBuilder("/user/test/a") should be(Some(DisabledPath))
    grouping.actorPathAttributeBuilder("/user/test/b") should be(Some(DisabledPath))
    grouping.actorPathAttributeBuilder("/user/test/a/b/c") should be(None)
    grouping.actorPathAttributeBuilder("/user/test/b/b/c") should be(None)
    grouping.actorPathAttributeBuilder("/user/test") should be(None)
    grouping.actorPathAttributeBuilder("/user/another") should be(None)
  }

  "ActorGrouping /**" should "create " in {
    val grouping = ActorGrouping.fromRule("/user/test/**", Reporting.group).value

    grouping.actorPathAttributeBuilder("/user/test/a") should be(Some(SomeActorPathAttribute("/user/test")))
    grouping.actorPathAttributeBuilder("/user/test/a/b/c") should be(Some(SomeActorPathAttribute("/user/test")))
    grouping.actorPathAttributeBuilder("/user/test") should be(Some(SomeActorPathAttribute("/user/test")))
    grouping.actorPathAttributeBuilder("/user/another") should be(None)
  }

  it should "create identity grouping for instance reporting" in {
    val grouping = ActorGrouping.fromRule("/user/test/**", Reporting.instance).value

    grouping.actorPathAttributeBuilder("/user/test/a") should be(Some(SomeActorPathAttribute("/user/test/a")))
    grouping.actorPathAttributeBuilder("/user/test/a/b/c") should be(Some(SomeActorPathAttribute("/user/test/a/b/c")))
    grouping.actorPathAttributeBuilder("/user/test") should be(None)
    grouping.actorPathAttributeBuilder("/user/another") should be(None)
  }

  it should "create disabled grouping for disabled reporting" in {
    val grouping = ActorGrouping.fromRule("/user/test/**", Reporting.disabled).value

    grouping.actorPathAttributeBuilder("/user/test/a") should be(Some(DisabledPath))
    grouping.actorPathAttributeBuilder("/user/test/a/b/c") should be(Some(DisabledPath))
    grouping.actorPathAttributeBuilder("/user/test") should be(None)
    grouping.actorPathAttributeBuilder("/user/another") should be(None)
  }

}
