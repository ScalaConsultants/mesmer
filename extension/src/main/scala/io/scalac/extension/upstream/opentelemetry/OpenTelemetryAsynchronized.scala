package io.scalac.extension.upstream.opentelemetry

import io.opentelemetry.api.metrics.Meter

import io.scalac.extension.metric.Asynchronized.CallbackListAsynchronized

trait OpenTelemetryAsynchronized extends CallbackListAsynchronized {

  protected val meter: Meter

  meter
    .longValueObserverBuilder("_pseudo_metric")
    .setDescription("This metric works like a hook for the exporter's loop")
    .build()
    .setCallback(_ => callCallbacks())

}
