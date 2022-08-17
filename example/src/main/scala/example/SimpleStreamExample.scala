package example

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.stream.KillSwitches
import akka.stream.UniqueKillSwitch
import akka.stream.scaladsl._
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import scala.concurrent.duration._

/**
 * Another example useful to testing if stream instrumentation work. It's a simple example that prints amount of process
 * elements roughly in 5 seconds intervals and after shutdown.
 *
 * Data should be compared with information that can be found in prometheus.
 */
object SimpleStreamExample extends App {

  val logger: Logger = LoggerFactory.getLogger(SimpleStreamExample.getClass)

  val config: Config = ConfigFactory.load("local.conf")

  implicit val system: ActorSystem[Nothing] =
    ActorSystem[Nothing](Behaviors.empty, "stream-simple", config)

  val ks: UniqueKillSwitch = Source
    .repeat(())
    .throttle(100, 1.second)
    .viaMat(KillSwitches.single)(Keep.right)
    .groupedWithin(1000, 5.seconds)
    .statefulMapConcat { () =>
      var timestamp         = System.nanoTime()
      var processedGlobally = 0
      bulk => {
        val current = System.nanoTime()
        val diff    = math.floorDiv(current - timestamp, 1_000_000)
        processedGlobally += bulk.size
        logger.info(s"Processed ${bulk.size} elements in ${diff} millis, in total $processedGlobally")
        timestamp = current
        Some(())
      }
    }
    .toMat(Sink.ignore)(Keep.left)
    .run()

  sys.addShutdownHook {
    ks.shutdown()
  }

}
