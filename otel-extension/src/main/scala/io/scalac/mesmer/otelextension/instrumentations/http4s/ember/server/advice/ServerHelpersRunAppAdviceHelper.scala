package io.scalac.mesmer.otelextension.instrumentations.http4s.ember.server.advice

import cats.data.Kleisli
import cats.effect.IO
import cats.implicits._
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.common.Attributes
import org.http4s.HttpApp
import org.http4s.Request
import org.http4s.Response

import scala.util.Try

object ServerHelpersRunAppAdviceHelper {

  private val meter = GlobalOpenTelemetry.getMeter("mesmer")

  private val requestsTotal = meter
    .counterBuilder("mesmer_http4s_ember_server_requests")
    .build()

  private val concurrentRequests = meter
    .upDownCounterBuilder("mesmer_http4s_ember_server_concurrent_requests")
    .build()

  private val requestDuration = meter
    .histogramBuilder("mesmer_http4s_ember_server_request_duration_seconds")
    .build()

  private def attributesForRequest(request: Request[IO]) =
    Attributes.builder().put("method", request.method.name).put("path", request.pathInfo.renderString)

  private def attributesForResponse(response: Response[IO]) =
    Attributes.builder().put("status", response.status.code.toString)

  def withMetrics(httpApp: Any): Kleisli[IO, Request[IO], Response[IO]] =
    Kleisli[IO, Request[IO], Response[IO]] { request =>
      val requestAttributes = attributesForRequest(request).build()
      val startTime         = System.nanoTime()

      concurrentRequests.add(1, requestAttributes)

      httpApp
        .asInstanceOf[HttpApp[IO]]
        .run(request)
        .attemptTap { response =>
          IO.fromTry(Try {
            val allAttributes = requestAttributes.toBuilder
              .putAll(
                response
                  .map(attributesForResponse(_).build())
                  .getOrElse(Attributes.empty())
              )
              .build()

            requestsTotal.add(1, allAttributes)

            requestDuration.record((System.nanoTime() - startTime) / 1e9d, allAttributes)

            concurrentRequests.add(-1, requestAttributes)
          })
        }
    }
}
