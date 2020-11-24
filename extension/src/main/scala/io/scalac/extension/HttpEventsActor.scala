package io.scalac.extension

import akka.actor.typed.Behavior
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.receptionist.Receptionist.Register
import akka.actor.typed.scaladsl.Behaviors
import io.scalac.extension.event.HttpEvent
import io.scalac.extension.event.HttpEvent._
import io.scalac.extension.metric.CachingMonitor._
import io.scalac.extension.metric.HttpMetricMonitor
import io.scalac.extension.metric.HttpMetricMonitor._

object HttpEventsActor {

  sealed trait Event extends SerializableMessage

  object Event {
    private[extension] final case class HttpEventWrapper(event: HttpEvent) extends Event
  }

  def apply(httpMetricMonitor: HttpMetricMonitor): Behavior[Event] = Behaviors.setup { ctx =>
    import Event._

    Receptionist(ctx.system).ref ! Register(httpService, ctx.messageAdapter(HttpEventWrapper.apply))
    
    def monitorHttp(
      inFlightRequest: Map[String, RequestStarted],
      cachedMonitors: Map[Labels, BoundMonitor]
    ): Behavior[Event] = {

      def getOrCreate(labels: Labels): (BoundMonitor, Map[Labels, BoundMonitor]) =
        cachedMonitors
          .get(labels)
          .fold {
            val newBoundMonitor = httpMetricMonitor.bind(labels)
            (newBoundMonitor, cachedMonitors + (labels -> newBoundMonitor))
          }(boundMonitor => (boundMonitor, cachedMonitors))
      Behaviors.receiveMessage {
        case HttpEventWrapper(started @ RequestStarted(id, _, path, method)) => {
          val (monitor, allMonitors) = getOrCreate(Labels(path, method))
          monitor.requestCounter.incValue(1L)

          monitorHttp(inFlightRequest + (id -> started), allMonitors)
        }
        case HttpEventWrapper(RequestCompleted(id, timestamp)) => {
          inFlightRequest
            .get(id)
            .fold {
              ctx.log.error("Got request completed event but no corresponding request started event")
              Behaviors.same[Event]
            } { started =>
              val requestDuration = timestamp - started.timestamp
              val monitorBoundary = Labels(started.path, started.method)

              val (monitor, allMonitors) = getOrCreate(monitorBoundary)
              monitor.requestTime.setValue(requestDuration)
              ctx.log.debug(s"request ${id} finished in {} millis", requestDuration)
              monitorHttp(inFlightRequest - id, allMonitors)
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
    }
    monitorHttp(Map.empty, Map.empty)
  }

}
