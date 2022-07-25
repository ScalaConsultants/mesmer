package io.scalac.mesmer.core.actor

import akka.actor.ActorPath
import akka.actor.ActorSystem
import com.typesafe.config.Config
import com.typesafe.config.ConfigValueType
import io.opentelemetry.api.common.Attributes
import org.slf4j.LoggerFactory

import scala.jdk.CollectionConverters._

import io.scalac.mesmer.core.ActorGrouping
import io.scalac.mesmer.core.SomeActorPathAttribute
import io.scalac.mesmer.core.akka.model.AttributeNames
import io.scalac.mesmer.core.config.ConfigurationUtils._
import io.scalac.mesmer.core.model.Reporting

trait ActorRefAttributeFactory {

  def forActorPath(path: ActorPath): Attributes

}

final class ConfiguredAttributeFactory(config: Config)(implicit val systems: ActorSystem)
    extends ActorRefAttributeFactory {
  private val actorConfigRoot = "io.scalac.mesmer.actor"
  private val logger          = LoggerFactory.getLogger(getClass)

  private lazy val reportingDefault: Reporting =
    config
      .tryValue(s"$actorConfigRoot.reporting-default")(_.getString)
      .toOption
      .map { rawValue =>
        Reporting.parse(rawValue).getOrElse {
          logger.warn(s"Value {} is not a proper setting for reporting - applying default disabled strategy", rawValue)
          Reporting.disabled
        }
      }
      .getOrElse(Reporting.disabled)

  implicit private lazy val matcherReversed: Ordering[ActorGrouping] =
    ActorGrouping.ordering.reverse

  private lazy val matchers: LazyList[ActorGrouping] =
    LazyList.from(
      config
        .tryValue(s"$actorConfigRoot.rules")(_.getObject)
        .toOption
        .map { configObject =>
          configObject.asScala.toList.flatMap { case (key, value) =>
            val raw = value.unwrapped()
            if (value.valueType() == ConfigValueType.STRING && raw.isInstanceOf[String]) {
              for {
                value <- Reporting.parse(raw.asInstanceOf[String])
                grouping = ActorGrouping.fromRule(key, value)
                _ = grouping.left.foreach { case ActorGrouping.InvalidRule(rule, message) =>
                  logger.error("Found invalid rule {} -> {}", rule, message)
                }
                matcher <- grouping.toOption
              } yield matcher
            } else None

          }.sorted
        }
        .getOrElse(Nil) ++ ActorGrouping.fromRule("/**", reportingDefault).toOption
    )

  def forActorPath(path: ActorPath): Attributes = {
    val actorPath = matchers
      .flatMap(_.actorPathAttributeBuilder(path.toStringWithoutAddress))
      .headOption
      .collect { case SomeActorPathAttribute(path) =>
        path
      }

    (Map(AttributeNames.ActorSystem -> systems.name) ++ actorPath.map(value => AttributeNames.ActorPath -> value))
      .foldLeft(Attributes.builder()) { case (builder, (key, value)) =>
        builder.put(key, value)
      }
      .build()
  }

}
