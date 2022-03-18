package example

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.stream.{ ActorMaterializer, Materializer }
import com.typesafe.config.ConfigFactory
import org.slf4j.{ Logger, LoggerFactory }
import akka.stream.scaladsl._
import scala.concurrent.duration._

object SimpleStreamExample extends App {

  val logger: Logger = LoggerFactory.getLogger(SimpleStreamExample.getClass)

//  val openTelemetry = new OpenTelemetrySetup().create()

  val config = ConfigFactory.load("local.conf")

  implicit val system: ActorSystem[Nothing] =
    ActorSystem[Nothing](Behaviors.empty, "stream-simple", config)

  val done = Source
    .repeat(())
    .throttle(100, 1.second)
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
    .runWith(Sink.ignore)

  sys.addShutdownHook {
    system.terminate()
  }
}
