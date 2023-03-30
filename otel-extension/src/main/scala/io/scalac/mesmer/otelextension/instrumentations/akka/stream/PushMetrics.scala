package io.scalac.mesmer.otelextension.instrumentations.akka.stream

/**
 * Command signalling that actor should send accumulated metrics in reply
 */
case object PushMetrics
