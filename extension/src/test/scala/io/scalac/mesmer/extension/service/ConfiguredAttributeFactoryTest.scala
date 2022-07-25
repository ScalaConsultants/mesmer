package io.scalac.mesmer.extension.service

import akka.actor.ActorPath
import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import io.opentelemetry.api.common.Attributes
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.jdk.CollectionConverters._

import io.scalac.mesmer.core.actor.ConfiguredAttributeFactory
import io.scalac.mesmer.core.akka.model.AttributeNames

object ConfiguredAttributeFactoryTest extends Matchers {
  val SystemName = "ConfigSystemName"

  implicit final class AttributeOps(private val attributes: Attributes) extends AnyVal {

    def haveActorPath(value: String): Unit = {

      attributesMap.get(AttributeNames.ActorSystem) should be(Some(SystemName))
      attributesMap.get(AttributeNames.ActorPath) should be(Some(value))
    }

    def haveOnlySystem(): Unit = {

      attributesMap.get(AttributeNames.ActorSystem) should be(Some(SystemName))
      attributesMap.get(AttributeNames.ActorPath) should be(None)
    }

    private def attributesMap: Map[String, String] = attributes
      .asMap()
      .asScala
      .map { case (key, value) =>
        key.getKey -> value.toString
      }
      .toMap

  }
}

class ConfiguredAttributeFactoryTest extends AnyFlatSpec with Matchers {
  import ConfiguredAttributeFactoryTest._

  implicit val system: ActorSystem = ActorSystem(SystemName)

  val TestConfig: String =
    """|
       |.rules {
       |  /user/actor/1
       |  /user/actor/2/*
       |}
       |""".stripMargin

  def actorPath(path: String): ActorPath = ActorPath.fromString(s"akka://$SystemName$path")

  "ConfigBasedConfigurationService" should "return default value for empty config" in {
    val sut  = new ConfiguredAttributeFactory(ConfigFactory.empty())
    val path = actorPath("/user/testactor")
    sut.forActorPath(path).haveOnlySystem()
  }

  it should "change default value" in {
    val config = ConfigFactory.parseString("""
                                             |io.scalac.mesmer.actor.reporting-default=instance
                                             |""".stripMargin)
    val sut  = new ConfiguredAttributeFactory(config)
    val path = actorPath("/user/testactor")
    sut.forActorPath(path).haveActorPath("/user/testactor")
  }

  it should "apply default value for not configured path" in {
    val config = ConfigFactory.parseString("""
                                             |io.scalac.mesmer.actor.rules {
                                             |  "/user/nonexistent" = instance
                                             |}
                                             |""".stripMargin)
    val sut  = new ConfiguredAttributeFactory(config)
    val path = actorPath("/user/testactor")
    sut.forActorPath(path).haveOnlySystem()
  }

  it should "apply specified value for exactly configured path" in {
    val config = ConfigFactory.parseString("""
                                             |io.scalac.mesmer.actor.rules {
                                             |  "/user/testactor" = instance
                                             |}
                                             |""".stripMargin)
    val sut  = new ConfiguredAttributeFactory(config)
    val path = actorPath("/user/testactor")
    sut.forActorPath(path).haveActorPath("/user/testactor")
  }

  it should "apply specific value for all values in wildcard" in {
    val config = ConfigFactory.parseString("""
                                             |io.scalac.mesmer.actor.rules {
                                             |  "/user/testactor/**" = instance
                                             |}
                                             |""".stripMargin)
    val sut = new ConfiguredAttributeFactory(config)

    sut.forActorPath(actorPath("/user/testactor")).haveOnlySystem()
    sut.forActorPath(actorPath("/user/testactor/a")).haveActorPath("/user/testactor/a")
    sut.forActorPath(actorPath("/user/testactor/b/c")).haveActorPath("/user/testactor/b/c")
    sut.forActorPath(actorPath("/user/otheractor")).haveOnlySystem()
  }

  it should "prefer exact rule over prefix" in {

    val config = ConfigFactory.parseString("""
                                             |io.scalac.mesmer.actor.rules {
                                             |  "/user/*" = group
                                             |  "/user/testactor" = instance
                                             |}
                                             |""".stripMargin)
    val sut = new ConfiguredAttributeFactory(config)

    sut.forActorPath(actorPath("/user/testactor")).haveActorPath("/user/testactor")
  }

  it should "more specifix prefix rules" in {

    val config = ConfigFactory.parseString("""
                                             |io.scalac.mesmer.actor.rules {
                                             |  "/user/**" = group
                                             |  "/user/more/specific/actor/**" = instance
                                             |}
                                             |""".stripMargin)
    val sut = new ConfiguredAttributeFactory(config)

    sut
      .forActorPath(actorPath("/user/more/specific/actor/some/name"))
      .haveActorPath("/user/more/specific/actor/some/name")
    sut.forActorPath(actorPath("/user/other/actor/name")).haveActorPath("/user")
  }

  it should "ignore incorrect rules" in {
    val incorrectConfig = ConfigFactory.parseString("""
                                                      |io.scalac.mesmer.actor.rules {
                                                      |  "/user/*/" = group
                                                      |  "user/more/specific/actor/" = instance
                                                      |}
                                                      |""".stripMargin)
    val sut = new ConfiguredAttributeFactory(incorrectConfig)

    sut.forActorPath(actorPath("/user/more/specific/actor/some/name")).haveOnlySystem()
    sut.forActorPath(actorPath("/user/other/actor/name")).haveOnlySystem()

  }
}
