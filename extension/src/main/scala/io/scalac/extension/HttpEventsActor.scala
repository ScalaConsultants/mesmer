package io.scalac.extension

import akka.actor.typed.Behavior
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.receptionist.Receptionist.Register
import akka.actor.typed.scaladsl.Behaviors
import io.scalac.extension.event.HttpEvent
import io.scalac.extension.event.HttpEvent._
import io.scalac.extension.metric.HttpMetricMonitor
import io.scalac.extension.metric.HttpMetricMonitor.BoundMonitor
import io.scalac.extension.model.{ Method, Path }

object HttpEventsActor {

  sealed trait Event extends SerializableMessage

  object Event {
    private[extension] final case class HttpEventWrapper(event: HttpEvent) extends Event
  }

  private case class Key(path: Path, method: Method)

  def apply(httpMetricMonitor: HttpMetricMonitor): Behavior[Event] = Behaviors.setup { ctx =>
    import Event._

    Receptionist(ctx.system).ref ! Register(httpService, ctx.messageAdapter(HttpEventWrapper.apply))

    def monitorHttp(
      inFlightRequest: Map[String, RequestStarted],
      cachedMonitors: Map[Key, BoundMonitor]
    ): Behavior[Event] = Behaviors.receiveMessage {
      case HttpEventWrapper(started @ RequestStarted(id, _, _, _)) => {
        monitorHttp(inFlightRequest + (id -> started), cachedMonitors)
      }
      case HttpEventWrapper(RequestCompleted(id, timestamp)) => {
        inFlightRequest
          .get(id)
          .fold {
            ctx.log.error("Got request completed event but no corresponding request started event")
            Behaviors.same[Event]
          } { started =>
            val requestDuration = timestamp - started.timestamp
            val monitorBoundary = Key(started.path, started.method)
            cachedMonitors
              .get(monitorBoundary)
              .fold {
                val boundMonitor = httpMetricMonitor.bind(started.path, started.method)
                boundMonitor.requestTime.setValue(requestDuration)
                ctx.log.debug(s"request ${id} finished in {} millis", requestDuration)

                monitorHttp(inFlightRequest - id, cachedMonitors + (monitorBoundary -> boundMonitor))
              } { boundMonitor =>
                boundMonitor.requestTime.setValue(requestDuration)
                ctx.log.debug(s"request ${id} finished in {} millis", requestDuration)
                monitorHttp(inFlightRequest - id, cachedMonitors)
              }
          }
      }
      case HttpEventWrapper(RequestFailed(id, timestamp)) => {
        inFlightRequest
          .get(id)
          .fold {
            ctx.log.error("Got request failed event but no corresponding request started event")
            Behaviors.same[Event]
          } { started =>
            val requestDuration = timestamp - started.timestamp
            ctx.log.error(s"request ${id} failed after {} millis", requestDuration)
            monitorHttp(inFlightRequest - id, cachedMonitors)
          }
      }
    }
    monitorHttp(Map.empty, Map.empty)
  }

}
