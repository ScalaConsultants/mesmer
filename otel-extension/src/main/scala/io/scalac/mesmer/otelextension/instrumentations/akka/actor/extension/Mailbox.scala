package io.scalac.mesmer.otelextension.instrumentations.akka.actor.extension

import akka.actor.ActorRef
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.metrics.{ ObservableLongMeasurement, ObservableLongUpDownCounter }
import io.scalac.mesmer.core.util.{ ActorCellOps, ActorRefOps }
import io.scalac.mesmer.otelextension.instrumentations.akka.actor.InstrumentsProvider

import java.util.Random

object Mailbox {

  val random = new Random()

  def initMailboxSizeGauge(ref: ActorRef, attributes: Attributes): ObservableLongUpDownCounter =
    InstrumentsProvider.instance().mailboxSizeCounter { (t: ObservableLongMeasurement) =>
      val cell               = ActorRefOps.cell(ref)
      val currentMailboxSize = ActorCellOps.numberOfMessages(cell)

      if (currentMailboxSize != 0) {
        println(s"I do happen, you just don't see me!!!!!. My size: $currentMailboxSize. My Path: ${ref.path.toString}")
      }

      val att = Attributes
        .builder()
        .put("foo", random.nextInt(2))
        .put("bar", random.nextInt(10))
        .build()


      println(att.toString)
      t.record(1, att)
    }

}
