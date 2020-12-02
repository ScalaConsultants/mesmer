package io.scalac.extension

import akka.actor.typed.Behavior
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.receptionist.Receptionist.Register
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.typed.Cluster
import io.scalac.extension.event.HttpEvent
import io.scalac.extension.event.HttpEvent._
import io.scalac.extension.metric.CachingMonitor._
import io.scalac.extension.metric.HttpMetricMonitor._
import io.scalac.extension.metric.{Bindable, HttpMetricMonitor}
import io.scalac.extension.model.{Method, Path, _}
import io.scalac.extension.service.PathService

object HttpEventsActor {

  sealed trait Event extends SerializableMessage

  object Event {
    private[extension] final case class HttpEventWrapper(event: HttpEvent) extends Event
  }

  def apply(httpMetricMonitor: HttpMetricMonitor, pathService: PathService): Behavior[Event] = Behaviors.setup { ctx =>
    import Event._

    Receptionist(ctx.system).ref ! Register(httpServiceKey, ctx.messageAdapter(HttpEventWrapper.apply))

    val cachingHttpMonitor: Bindable.Aux[Labels, httpMetricMonitor.Bound] =
      httpMetricMonitor.caching

    def createLabels(path: Path, method: Method): Labels = Labels(pathService.template(path), method)

    val selfNodeAddress = Cluster(ctx.system).selfMember.uniqueAddress.toNode

    def monitorHttp(
      inFlightRequest: Map[String, RequestStarted]
    ): Behavior[Event] =
      Behaviors.receiveMessage {
        case HttpEventWrapper(started @ RequestStarted(id, _, path, method)) => {
          val monitor = cachingHttpMonitor.bind(createLabels(path, method))

          monitor.requestCounter.incValue(1L)

          monitorHttp(inFlightRequest + (id -> started))
        }
        case HttpEventWrapper(RequestCompleted(id, timestamp)) => {
          inFlightRequest
            .get(id)
            .fold {
              ctx.log.error("Got request completed event but no corresponding request started event")
              Behaviors.same[Event]
            } { started =>
              val requestDuration = timestamp - started.timestamp
              val monitorBoundary = createLabels(started.path, started.method)
              val monitor         = cachingHttpMonitor.bind(monitorBoundary)

              monitor.requestTime.setValue(requestDuration)
              ctx.log.debug(s"request ${id} finished in {} millis", requestDuration)
              monitorHttp(inFlightRequest - id)
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
              monitorHttp(inFlightRequest - id)
            }
        }
      }
    monitorHttp(Map.empty)
  }

}
