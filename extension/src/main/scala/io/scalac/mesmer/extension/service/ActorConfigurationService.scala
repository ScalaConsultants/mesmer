package io.scalac.mesmer.extension.service

import akka.{ actor => classic }
import com.typesafe.config.Config
import com.typesafe.config.ConfigValueType
import org.slf4j.LoggerFactory

import scala.jdk.CollectionConverters._

import io.scalac.mesmer.core.PathMatcher
import io.scalac.mesmer.core.model.ActorConfiguration
import io.scalac.mesmer.extension.config.ConfigurationUtils._

trait ActorConfigurationService {
  def forActorPath(ref: classic.ActorPath): ActorConfiguration
}

object ConfigBasedConfigurationService {
  final val actorConfigRoot = "io.scalac.mesmer.actor"
}

final class ConfigBasedConfigurationService(config: Config) extends ActorConfigurationService {
  import ConfigBasedConfigurationService._

  private val logger = LoggerFactory.getLogger(getClass)

  private lazy val reportingDefault: ActorConfiguration.Reporting =
    config
      .tryValue(s"$actorConfigRoot.reporting-default")(_.getString)
      .toOption
      .map { rawValue =>
        ActorConfiguration.Reporting.parse(rawValue).getOrElse {
          logger.warn(s"Value {} is not a proper setting for reporting - default is set to disabled", rawValue)
          ActorConfiguration.Reporting.disabled
        }
      }
      .getOrElse(ActorConfiguration.Reporting.disabled)

  private lazy val matchers: List[PathMatcher[ActorConfiguration.Reporting]] =
    config
      .tryValue(s"$actorConfigRoot.rules")(_.getObject)
      .toOption
      .map { configObject =>
        configObject.asScala.toList.flatMap { case (key, value) =>
          val raw = value.unwrapped()
          if (value.valueType() == ConfigValueType.STRING && raw.isInstanceOf[String]) {
            for {
              value   <- ActorConfiguration.Reporting.parse(raw.asInstanceOf[String])
              matcher <- PathMatcher.parse(key, value)
            } yield matcher
          } else None

        }
      }
      .getOrElse(Nil)

  implicit private val matcherReversed: Ordering[PathMatcher[ActorConfiguration.Reporting]] =
    Ordering[PathMatcher[ActorConfiguration.Reporting]].reverse

  def forActorPath(ref: classic.ActorPath): ActorConfiguration = {
    val reporting = matchers
      .filter(_.matches(ref.toStringWithoutAddress))
      .sorted
      .headOption
      .map(_.value)
      .getOrElse(reportingDefault)
    ActorConfiguration(reporting)
  }
}
