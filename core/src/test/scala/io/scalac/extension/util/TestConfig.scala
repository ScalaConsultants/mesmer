package io.scalac.extension.util
import com.typesafe.config._

object TestConfig {
  lazy val localActorProvider =
    ConfigFactory.parseString("akka.actor.provider=local").withFallback(ConfigFactory.load("application-test"))
}
