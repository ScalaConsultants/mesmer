package io.scalac.extension

import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.receptionist.Receptionist.{ Deregister, Register }
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ Behavior, PostStop }
import akka.util.Timeout

import io.scalac.extension.event.HttpEvent
import io.scalac.extension.event.HttpEvent._
import io.scalac.extension.http.RequestStorage
import io.scalac.extension.metric.HttpMetricMonitor
import io.scalac.extension.metric.HttpMetricMonitor._
import io.scalac.core.model.{ Method, Path, _ }
import io.scalac.extension.service.PathService
import scala.language.postfixOps

class HttpEventsActor

object HttpEventsActor {

  sealed trait Event extends SerializableMessage

  object Event {
    private[HttpEventsActor] final case class HttpEventWrapper(event: HttpEvent) extends Event
  }

  def apply(
    httpMetricMonitor: HttpMetricMonitor,
    initRequestStorage: RequestStorage,
    pathService: PathService,
    node: Option[Node] = None
  )(implicit timeout: Timeout): Behavior[Event] = Behaviors.setup { ctx =>
    import Event._

    val receptionistAdapter = ctx.messageAdapter(HttpEventWrapper.apply)

    Receptionist(ctx.system).ref ! Register(httpServiceKey, ctx.messageAdapter(HttpEventWrapper.apply))

    def createLabels(path: Path, method: Method): Labels = Labels(node, pathService.template(path), method)

    def monitorHttp(
      requestStorage: RequestStorage
    ): Behavior[Event] =
      Behaviors
        .receiveMessage[Event] {
          case HttpEventWrapper(started @ RequestStarted(id, _, path, method)) =>
            val monitor = httpMetricMonitor.bind(createLabels(path, method))

            monitor.requestCounter.incValue(1L)

            monitorHttp(requestStorage.requestStarted(started))
          case HttpEventWrapper(completed @ RequestCompleted(id, timestamp)) =>
            requestStorage
              .requestCompleted(completed)
              .fold {
                ctx.log.error("Got request completed event but no corresponding request started event")
                Behaviors.same[Event]
              } { case (storage, started) =>
                val requestDuration = started.timestamp.interval(timestamp)
                val monitorBoundary = createLabels(started.path, started.method)
                val monitor         = httpMetricMonitor.bind(monitorBoundary)

                monitor.requestTime.setValue(requestDuration)
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
