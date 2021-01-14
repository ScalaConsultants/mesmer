package io.scalac.core.util

import akka.actor.{Actor, ActorLogging, ActorSystem, ExtendedActorSystem, Props}
import com.typesafe.config.ConfigFactory
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers

import scala.util.Random

class ActorsTest extends AnyFlatSpec with Matchers {

  class Dummy extends Actor with ActorLogging {
    import Dummy._
    private var childrenIdCounter = 0L
    override def receive: Receive = {
      case Spawn => {
        context.actorOf(Dummy.props, s"alpha$childrenIdCounter")
        childrenIdCounter += 1L
      }
      case mess => log.info("[{}]", mess)
    }
  }

  object Dummy {
    case object Spawn
    def props = Props(new Dummy)
  }

  "ActorUtils" should "return actor tree in flat seq" in {
    val config = ConfigFactory
      .parseString("akka.actor.provider=local")
      .withFallback(ConfigFactory.load())

    val system = ActorSystem("testingSystem", config)
    val names = List.tabulate(100)(num => s"dummy_$num")
    for {
    name <- names
    ref = system.actorOf(Dummy.props, name)
    children <- 0 to Random.nextInt(5)
    } ref ! Dummy.Spawn

    Thread.sleep(1000)
    Actors.getActorsFlat(system.asInstanceOf[ExtendedActorSystem]).foreach(println)
  }
}
