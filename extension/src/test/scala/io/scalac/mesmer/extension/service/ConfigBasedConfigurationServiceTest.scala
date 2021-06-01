package io.scalac.mesmer.extension.service

import akka.actor.ActorPath
import com.typesafe.config.ConfigFactory
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import io.scalac.mesmer.core.model.ActorConfiguration._

class ConfigBasedConfigurationServiceTest extends AnyFlatSpec with Matchers {
  val TestConfig: String =
    """|
       |.rules {
       |  /user/actor/1
       |  /user/actor/2/*
       |}
       |""".stripMargin

  def actorPath(path: String): ActorPath = ActorPath.fromString(s"akka://testsystem${path}")

  "ConfigBasedConfigurationService" should "return default value for empty config" in {
    val sut  = new ConfigBasedConfigurationService(ConfigFactory.empty())
    val path = actorPath("/user/testactor")
    sut.forActorPath(path) should be(disabledConfig)
  }

  it should "change default value" in {
    val config = ConfigFactory.parseString("""
                                             |io.scalac.mesmer.actor.reporting-default=group
                                             |""".stripMargin)
    val sut    = new ConfigBasedConfigurationService(config)
    val path   = actorPath("/user/testactor")
    sut.forActorPath(path) should be(groupingConfig)
  }

  it should "apply default value for not configured path" in {
    val config = ConfigFactory.parseString("""
                                             |io.scalac.mesmer.actor.rules {
                                             |  "/user/nonexistent" = instance
                                             |}
                                             |""".stripMargin)
    val sut    = new ConfigBasedConfigurationService(config)
    val path   = actorPath("/user/testactor")
    sut.forActorPath(path) should be(disabledConfig)
  }

  it should "apply specified value for exactly configured path" in {
    val config = ConfigFactory.parseString("""
                                             |io.scalac.mesmer.actor.rules {
                                             |  "/user/testactor" = instance
                                             |}
                                             |""".stripMargin)
    val sut    = new ConfigBasedConfigurationService(config)
    val path   = actorPath("/user/testactor")
    sut.forActorPath(path) should be(instanceConfig)
  }

  it should "apply specific value for all values in wildcard" in {
    val config = ConfigFactory.parseString("""
                                             |io.scalac.mesmer.actor.rules {
                                             |  "/user/testactor/*" = instance
                                             |}
                                             |""".stripMargin)
    val sut    = new ConfigBasedConfigurationService(config)

    sut.forActorPath(actorPath("/user/testactor")) should be(instanceConfig)
    sut.forActorPath(actorPath("/user/testactor/a")) should be(instanceConfig)
    sut.forActorPath(actorPath("/user/testactor/b/c/")) should be(instanceConfig)
    sut.forActorPath(actorPath("/user/otheractor")) should be(disabledConfig)
  }

  it should "prefer exact rule over prefix" in {

    val config = ConfigFactory.parseString("""
                                             |io.scalac.mesmer.actor.rules {
                                             |  "/user/*" = group
                                             |  "/user/testactor" = instance
                                             |}
                                             |""".stripMargin)
    val sut    = new ConfigBasedConfigurationService(config)

    sut.forActorPath(actorPath("/user/testactor")) should be(instanceConfig)
  }

  it should "more specifix prefix rules" in {

    val config = ConfigFactory.parseString("""
                                             |io.scalac.mesmer.actor.rules {
                                             |  "/user/*" = group
                                             |  "/user/more/specific/actor/*" = instance
                                             |}
                                             |""".stripMargin)
    val sut    = new ConfigBasedConfigurationService(config)

    sut.forActorPath(actorPath("/user/more/specific/actor/some/name")) should be(instanceConfig)
    sut.forActorPath(actorPath("/user/other/actor/name")) should be(groupingConfig)
  }

  it should "ignore incorrect rules" in {
    val incorrectConfig = ConfigFactory.parseString("""
                                                      |io.scalac.mesmer.actor.rules {
                                                      |  "/user/*/" = group
                                                      |  "user/more/specific/actor/" = instance
                                                      |}
                                                      |""".stripMargin)
    val sut             = new ConfigBasedConfigurationService(incorrectConfig)

    sut.forActorPath(actorPath("/user/more/specific/actor/some/name")) should be(disabledConfig)
    sut.forActorPath(actorPath("/user/other/actor/name")) should be(disabledConfig)

  }
}
