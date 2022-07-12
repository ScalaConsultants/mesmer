package io.scalac.mesmer.instrumentation.akka.persistence

import _root_.akka.util.Timeout
import akka.actor.typed.ActorRef
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.sdk.metrics.data.{ MetricData, MetricDataType }
import io.scalac.mesmer.agent.utils.DummyEventSourcedActor.{ DoNothing, Persist }
import io.scalac.mesmer.agent.utils.{ DummyEventSourcedActor, OtelAgentTest, SafeLoadSystem }
import io.scalac.mesmer.core.akka.model.AttributeNames
import io.scalac.mesmer.core.util.ReceptionistOps
import org.scalatest.concurrent.Eventually
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{ BeforeAndAfterEach, Inspectors, OptionValues }

import java.util.UUID
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

class OtelAkkaPersistenceAgentTest
    extends AnyFlatSpec
    with OtelAgentTest
    with ReceptionistOps
    with OptionValues
    with Eventually
    with Matchers
    with SafeLoadSystem
    with BeforeAndAfterEach
    with Inspectors {

  implicit val askTimeout: Timeout = Timeout(1.minute)

  type Fixture = (String, ActorRef[DummyEventSourcedActor.Command])

  private def checkCount(id: String)(num: Int): MetricData => Unit = data =>
    if (data.getType == MetricDataType.HISTOGRAM) {
      val points = data.getHistogramData.getPoints.asScala
        .filter(point =>
          Option(point.getAttributes.get(AttributeKey.stringKey(AttributeNames.EntityPath)))
            .exists(_.contains(id))
        )
        .head
      points.getCount should be(num)
    } else {
      val points = data.getLongSumData.getPoints.asScala
        .filter(point =>
          Option(point.getAttributes.get(AttributeKey.stringKey(AttributeNames.EntityPath)))
            .exists(_.contains(id))
        )
        .head
      points.getValue should be(num)
    }

  def test(body: Fixture => Any): Any = {

    val id  = UUID.randomUUID()
    val ref = system.systemActorOf(DummyEventSourcedActor(id), id.toString)

    Function.untupled(body)(id.toString, ref)
  }

  "AkkaPersistenceAgent" should "generate only recovery events" in test { case (id, actor) =>
    actor ! DoNothing

    val checkRecovery = checkCount(id)(1)

    val checkMetricEmpty: MetricData => Unit = data =>
      if (!data.isEmpty) {
        checkCount(id)(0)
      }

    assertMetrics("mesmer")(
      "persistence_recovery" -> checkRecovery,
      "persistence_event"    -> checkMetricEmpty,
      "persistence_snapshot" -> checkMetricEmpty
    )
  }

  it should "generate recovery, persisting and snapshot events for single persist event" in test { case (id, actor) =>
    actor ! Persist

    val check: MetricData => Unit = checkCount(id)(1)

    assertMetrics("mesmer")(
      "persistence_recovery" -> check,
      "persistence_event"    -> check,
      "persistence_snapshot" -> check
    )
  }

  it should "generate recovery, persisting and snapshot events for multiple persist event" in test { case (id, actor) =>
    List.fill(5)(Persist).foreach(actor.tell)

    def check(num: Int) = checkCount(id)(num)

    assertMetrics("mesmer")(
      "persistence_recovery" -> check(1),
      "persistence_event"    -> check(5),
      "persistence_snapshot" -> check(5)
    )
  }

}
