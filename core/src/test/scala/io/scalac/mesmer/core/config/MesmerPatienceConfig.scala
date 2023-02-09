package io.scalac.mesmer.core.config

import org.scalatest.concurrent.PatienceConfiguration
import org.scalatest.time.Span

import scala.concurrent.duration._

trait MesmerPatienceConfig {
  this: PatienceConfiguration =>

  val reasonableInterval: Span = scaled(200.millis)
  val reasonableTimeout: Span  = scaled(15.seconds)

  override implicit lazy val patienceConfig: PatienceConfig = PatienceConfig(reasonableTimeout, reasonableInterval)
}
