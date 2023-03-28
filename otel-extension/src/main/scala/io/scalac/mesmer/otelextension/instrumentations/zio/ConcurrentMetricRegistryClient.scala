package io.scalac.mesmer.otelextension.instrumentations.zio

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.time.Instant

import zio.Unsafe
import zio.metrics.MetricKey
import zio.metrics.MetricKeyType
import zio.metrics.MetricPair

class ConcurrentMetricRegistryClient(metricsRegistry: AnyRef) {
  import ConcurrentMetricRegistryClient._

  private lazy val unsafe = {
    val companionObject = Class.forName("zio.Unsafe$")
    val method          = companionObject.getMethod("unsafe")
    method.setAccessible(true)
    method.invoke(companionObject.getField("MODULE$").get(null))
  }

  private lazy val snapshotCall = {
    val method = metricsRegistry.getClass.getDeclaredMethod("snapshot", classOf[Unsafe])
    method.setAccessible(true)
    () => method.invoke(metricsRegistry, unsafe).asInstanceOf[Set[MetricPair.Untyped]]
  }

  private lazy val getMethod = {
    val method = metricsRegistry.getClass.getDeclaredMethod("get", classOf[MetricKey[_]], classOf[Unsafe])
    method.setAccessible(true)
    method
  }

  private lazy val metricsListener = Class.forName("zio.metrics.MetricListener")

  private lazy val addListenerCall =
    metricsRegistry.getClass.getDeclaredMethod(
      "addListener",
      metricsListener,
      classOf[Unsafe]
    )

  def snapshot(): Set[MetricPair.Untyped] =
    snapshotCall()

  def get[Type <: MetricKeyType](key: MetricKey[Type]): MetricHook[key.keyType.In, key.keyType.Out] = {
    val hook = getMethod.invoke(metricsRegistry, key, unsafe)
    // TODO can hook.getClass.getDeclaredField reflective calls be optimised more?
    MetricHook(
      {
        val field = hook.getClass.getDeclaredField("update")
        field.setAccessible(true)
        field.get(hook).asInstanceOf[key.keyType.In => Unit]
      }, {
        val field = hook.getClass.getDeclaredField("get")
        field.setAccessible(true)
        field.get(hook).asInstanceOf[() => key.keyType.Out]
      }, {
        val field = hook.getClass.getDeclaredField("modify")
        field.setAccessible(true)
        field.get(hook).asInstanceOf[key.keyType.In => Unit]
      }
    )
  }

  def addListener(listener: MetricListener): Unit = {
    val invocationHandler = new InvocationHandler {
      override def invoke(proxy: Any, method: Method, args: Array[AnyRef]): AnyRef = {
        method.getName match {
          case "updateCounter" =>
            listener.updateCounter(
              args.head.asInstanceOf[MetricKey[MetricKeyType.Counter]],
              args(1).asInstanceOf[Double]
            )(args(2).asInstanceOf[Unsafe])
          case "updateGauge" =>
            listener.updateGauge(
              args.head.asInstanceOf[MetricKey[MetricKeyType.Gauge]],
              args(1).asInstanceOf[Double]
            )(args(2).asInstanceOf[Unsafe])
          case "updateFrequency" =>
            listener.updateFrequency(
              args.head.asInstanceOf[MetricKey[MetricKeyType.Frequency]],
              args(1).asInstanceOf[String]
            )(args(2).asInstanceOf[Unsafe])
          case "updateSummary" =>
            listener.updateSummary(
              args.head.asInstanceOf[MetricKey[MetricKeyType.Summary]],
              args(1).asInstanceOf[Double],
              args(2).asInstanceOf[Instant]
            )(args(3).asInstanceOf[Unsafe])
          case "updateHistogram" =>
            listener.updateHistogram(
              args.head.asInstanceOf[MetricKey[MetricKeyType.Histogram]],
              args(1).asInstanceOf[Double]
            )(args(2).asInstanceOf[Unsafe])
        }
        ().asInstanceOf[AnyRef]
      }
    }
    val proxy = java.lang.reflect.Proxy.newProxyInstance(
      getClass.getClassLoader,
      Array(metricsListener),
      invocationHandler
    )
    addListenerCall.invoke(metricsRegistry, proxy, unsafe)
    ()
  }
}

object ConcurrentMetricRegistryClient {
  case class MetricHook[In, Out](
    update: In => Unit,
    get: () => Out,
    modify: In => Unit
  )

  trait MetricListener {
    def updateHistogram(key: MetricKey[MetricKeyType.Histogram], value: Double)(implicit unsafe: Unsafe): Unit = ()

    def updateGauge(key: MetricKey[MetricKeyType.Gauge], value: Double)(implicit unsafe: Unsafe): Unit = ()

    def updateFrequency(key: MetricKey[MetricKeyType.Frequency], value: String)(implicit unsafe: Unsafe): Unit = ()

    def updateSummary(key: MetricKey[MetricKeyType.Summary], value: Double, instant: java.time.Instant)(implicit
      unsafe: Unsafe
    ): Unit = ()

    def updateCounter(key: MetricKey[MetricKeyType.Counter], value: Double)(implicit unsafe: Unsafe): Unit = ()
  }
}
