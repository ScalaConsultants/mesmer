package io.scalac.extension

import akka.remote.testkit.MultiNodeConfig
import com.typesafe.config.ConfigFactory

object ThreeNodesConfig extends MultiNodeConfig {

  val node1 = role("node1")
  val node2 = role("node2")
  val node3 = role("node3")

  testTransport(true)
  commonConfig(ConfigFactory.parseString("""
                                           |akka.loglevel=WARNING
                                           |akka.actor.provider = cluster
                                           |akka.remote.artery.enabled = on
                                           |akka.coordinated-shutdown.run-by-jvm-shutdown-hook = off
                                           |akka.coordinated-shutdown.terminate-actor-system = off
                                           |akka.cluster.run-coordinated-shutdown-when-down = off
    """.stripMargin))

}
