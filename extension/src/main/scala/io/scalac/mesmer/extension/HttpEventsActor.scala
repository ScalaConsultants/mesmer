package io.scalac.mesmer.extension

import akka.actor.typed.Behavior
import akka.actor.typed.PostStop
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.receptionist.Receptionist.Deregister
import akka.actor.typed.receptionist.Receptionist.Register
import akka.actor.typed.scaladsl.Behaviors
import akka.util.Timeout

import io.scalac.mesmer.core._
import io.scalac.mesmer.core.event.HttpEvent
import io.scalac.mesmer.core.event.HttpEvent._
import io.scalac.mesmer.core.model.Method
import io.scalac.mesmer.core.model.Path
import io.scalac.mesmer.core.model._
import io.scalac.mesmer.extension.http.RequestStorage
import io.scalac.mesmer.extension.metric.HttpConnectionMetricsMonitor
import io.scalac.mesmer.extension.metric.HttpMetricsMonitor
import io.scalac.mesmer.extension.service.PathService

class HttpEventsActor

object HttpEventsActor {

  sealed trait Event extends SerializableMessage

  object Event {
    private[HttpEventsActor] final case class HttpEventWrapper(event: HttpEvent) extends Event
  }

  def apply(
    httpMetricMonitor: HttpMetricsMonitor,
    httpConnectionMetricMonitor: HttpConnectionMetricsMonitor,
    initRequestStorage: RequestStorage,
    pathService: PathService,
    node: Option[Node] = None
  )(implicit timeout: Timeout): Behavior[Event] = Behaviors.setup { ctx =>
    import Event._

    val receptionistAdapter = ctx.messageAdapter(HttpEventWrapper.apply)

    Receptionist(ctx.system).ref ! Register(httpServiceKey, ctx.messageAdapter(HttpEventWrapper.apply))

    def createConnectionLabels(connectionEvent: ConnectionEvent): HttpConnectionMetricsMonitor.Labels =
      HttpConnectionMetricsMonitor.Labels(node, connectionEvent.interface, connectionEvent.port)

    def createRequestLabels(path: Path, method: Method, status: Status): HttpMetricsMonitor.Labels =
      HttpMetricsMonitor.Labels(node, pathService.template(path), method, status)

    def monitorHttp(
      requestStorage: RequestStorage
    ): Behavior[Event] =
      Behaviors
        .receiveMessage[Event] {

          case HttpEventWrapper(connectionEvent: ConnectionEvent) =>
            val counter = httpConnectionMetricMonitor.bind(createConnectionLabels(connectionEvent)).connectionCounter
            connectionEvent match {
              case _: ConnectionStarted   => counter.incValue(1L)
              case _: ConnectionCompleted => counter.decValue(1L)
            }
            Behaviors.same

          case HttpEventWrapper(started: RequestStarted) =>
            monitorHttp(requestStorage.requestStarted(started))

          case HttpEventWrapper(completed @ RequestCompleted(id, timestamp, status)) =>
            requestStorage
              .requestCompleted(completed)
              .fold {
                ctx.log.error("Got request completed event but no corresponding request started event")
                Behaviors.same[Event]
              } { case (storage, started) =>
                val requestDuration = started.timestamp.interval(timestamp)
                val monitorBoundary = createRequestLabels(started.path, started.method, status)
                val monitor         = httpMetricMonitor.bind(monitorBoundary)

                monitor.requestTime.setValue(requestDuration)
                monitor.requestCounter.incValue(1L)

                ctx.log.debug("request {} finished in {} millis", id, requestDuration)
                monitorHttp(storage)
              }

          case HttpEventWrapper(failed @ RequestFailed(id, timestamp)) =>
            requestStorage
              .requestFailed(failed)
              .fold {
                ctx.log.error("Got request failed event but no corresponding request started event")
                Behaviors.same[Event]
              } { case (storage, started) =>
                val requestDuration = started.timestamp.interval(timestamp)
                ctx.log.error("request {} failed after {} millis", id, requestDuration)
                monitorHttp(storage)
              }

        }
        .receiveSignal { case (_, PostStop) =>
          ctx.log.info("Http events monitor terminated")
          Receptionist(ctx.system).ref ! Deregister(httpServiceKey, receptionistAdapter)
          Behaviors.same
        }
    monitorHttp(initRequestStorage)
  }

}
