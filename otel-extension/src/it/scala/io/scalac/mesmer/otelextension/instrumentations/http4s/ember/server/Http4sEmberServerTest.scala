package io.scalac.mesmer.otelextension.instrumentations.http4s.ember.server

import cats.effect._
import cats.effect.unsafe.implicits.global
import cats.syntax.all._
import com.comcast.ip4s._
import io.opentelemetry.api.common.Attributes
import io.scalac.mesmer.agent.utils.OtelAgentTest
import io.scalac.mesmer.core.config.MesmerPatienceConfig
import org.http4s.HttpApp
import org.http4s.HttpRoutes
import org.http4s.Uri
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.scalatest.BeforeAndAfterEach
import org.scalatest.Inside
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

import scala.jdk.CollectionConverters._

class Http4sEmberServerTest
    extends AnyFreeSpec
    with OtelAgentTest
    with Matchers
    with MesmerPatienceConfig
    with BeforeAndAfterEach
    with Inside {
  import Http4sEmberServerTest._

  private def service(block: () => Unit): HttpApp[IO] = {
    import org.http4s.dsl.io._

    HttpRoutes
      .of[IO] { case GET -> Root =>
        block()
        Ok("")
      }
      .orNotFound
  }

  private def url(address: SocketAddress[Host], path: String = ""): Uri =
    Uri.unsafeFromString(
      s"http://${Uri.Host.fromIp4sHost(address.host).renderString}:${address.port.value}$path"
    )

  private def server(block: () => Unit) = EmberServerBuilder
    .default[IO]
    .withHttpApp(service(block))
    .withPort(port"0")
    .build

  private val client = EmberClientBuilder.default[IO].build

  private def doGetRootCall(block: () => Unit = () => ()) = {
    server(block)
      .use(server =>
        client.use(client =>
          client
            .get(url(server.addressIp4s))(_.status.pure[IO])
        )
      )
      .unsafeRunSync()
    ()
  }

  "http4s ember server" - {
    "should record" - {
      "requests" - {
        "total counter" in {
          doGetRootCall()

          assertMetric("mesmer_http4s_ember_server_requests") { data =>
            inside(data.getLongSumData.getPoints.asScala.toList) { case List(point) =>
              point.getValue shouldEqual 1
              point.getAttributes.asScalaMap() should contain theSameElementsAs Map(
                "method" -> "GET",
                "path"   -> "/",
                "status" -> "200"
              )
            }
          }
        }

        "duration histogram" in {
          doGetRootCall()

          assertMetric("mesmer_http4s_ember_server_request_duration_seconds") { data =>
            inside(data.getHistogramData.getPoints.asScala.toList) { case List(point) =>
              point.getAttributes.asScalaMap() should contain theSameElementsAs Map(
                "method" -> "GET",
                "path"   -> "/",
                "status" -> "200"
              )
            }
          }
        }

        "concurrent counter" in {
          val expectedAttributes = Map(
            "method" -> "GET",
            "path"   -> "/"
          )

          val assertZeroConcurrentRequests = () =>
            assertMetric("mesmer_http4s_ember_server_concurrent_requests") { data =>
              inside(data.getLongSumData.getPoints.asScala.toList) { case List(point) =>
                point.getValue shouldEqual 0
                point.getAttributes.asScalaMap() should contain theSameElementsAs expectedAttributes
              }
            }

          assertZeroConcurrentRequests()

          doGetRootCall { () =>
            assertMetric("mesmer_http4s_ember_server_concurrent_requests") { data =>
              inside(data.getLongSumData.getPoints.asScala.toList) { case List(point) =>
                point.getValue shouldEqual 1
                point.getAttributes.asScalaMap() should contain theSameElementsAs expectedAttributes
              }
            }
          }

          assertZeroConcurrentRequests()
        }
      }
    }
  }
}

object Http4sEmberServerTest {
  implicit class AttributesAsMap(attributes: Attributes) {
    def asScalaMap(): Map[String, AnyRef] =
      attributes
        .asMap()
        .asScala
        .map { case (k, v) =>
          (k.getKey, v)
        }
        .toMap
  }
}
