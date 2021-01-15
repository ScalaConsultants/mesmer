package io.scalac.core.util

import akka.actor.{ actorRef2Scala, Actor, ActorLogging, ActorRef, ActorSystem, ExtendedActorSystem, Props }
import com.typesafe.config.ConfigFactory
import io.scalac.core.model.ActorNode
import io.scalac.extension.util.TestOps
import org.scalatest.concurrent.Eventually
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.util.Random

class ActorDiscoveryTest extends AnyFlatSpec with Matchers with TestOps with Eventually {

  class Dummy extends Actor with ActorLogging {
    import Dummy._

    override def receive: Receive = {
      case Spawn(id) => {
        val name = createName(id)
        if (context.child(name).isEmpty)
          context.actorOf(Dummy.props, name)
      }
      case mess => log.info("[{}]", mess)
    }
  }

  object Dummy {
    def createName(id: Int): String = s"alpha$id"
    case class Spawn(id: Int)
    def props = Props(new Dummy)
  }

  "ActorUtils" should "return actor tree in flat seq" in {
    val config = ConfigFactory
      .parseString("akka.actor.provider=local")
      .withFallback(ConfigFactory.load())

    val system = ActorSystem(createUniqueId, config)

    val namePrefix = "dummy_"
    val names      = List.tabulate(100)(num => s"$namePrefix$num")

    val children = for {
      name <- names
      ref  = system.actorOf(Dummy.props, name)
      id   <- 0 to Random.nextInt(5)
    } yield {
      ref ! Dummy.Spawn(id)
      (name, Dummy.createName(id))
    }

    val actors = ActorDiscovery.allActorsFlat(system.asInstanceOf[ExtendedActorSystem])
    actors.foreach(println)
    actors.filter(_.child.name == "system") should have size (1)
    actors.filter(_.child.name == "user") should have size (1)
    actors.count(_.parent.name == "system") should be > 0
    actors.filter(_.child.name.startsWith(namePrefix)) should have size (names.size)
    actors.collect {
      case ActorNode(_, child) if child.name.startsWith(namePrefix) => child.name
    } should contain theSameElementsAs (names)

    eventually {
      actors.collect {
        case ActorNode(parent, child) if names.contains(parent.name) => (parent.name -> child.name)
      } should contain theSameElementsAs (children)
    }

    system.terminate()
  }

  it should "detect local actors in a cluster" in {
    val config = ConfigFactory
      .parseString("akka.actor.provider=cluster")
      .withFallback(ConfigFactory.load())

    val system = ActorSystem(createUniqueId, config)

    val namePrefix = "dummy_"
    val names      = List.tabulate(100)(num => s"$namePrefix$num")

    val children = for {
      name <- names
      ref  = system.actorOf(Dummy.props, name)
      id   <- 0 to Random.nextInt(5)
    } yield {
      ref ! Dummy.Spawn(id)
      (name, Dummy.createName(id))
    }

    val actors = ActorDiscovery.allActorsFlat(system.asInstanceOf[ExtendedActorSystem])
    actors.foreach(println)
    actors.filter(_.child.name == "system") should have size (1)
    actors.filter(_.child.name == "user") should have size (1)
    actors.count(_.parent.name == "system") should be > 0
    actors.filter(_.child.name.startsWith(namePrefix)) should have size (names.size)
    actors.collect {
      case ActorNode(_, child) if child.name.startsWith(namePrefix) => child.name
    } should contain theSameElementsAs (names)

    eventually {
      actors.collect {
        case ActorNode(parent, child) if names.contains(parent.name) => (parent.name -> child.name)
      } should contain theSameElementsAs (children)
    }
    system.terminate()
  }

  it should "return empty Seq when nonfatal error occur" in {
    val config = ConfigFactory
      .parseString("akka.actor.provider=local")
      .withFallback(ConfigFactory.load())

    implicit val system: ActorSystem = ActorSystem(createUniqueId, config)
    val actors                       = ActorDiscovery.getActorsFrom(null: ActorRef)
    actors should be(Seq.empty)
  }
}
