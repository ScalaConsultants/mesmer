package io.scalac.mesmer.otelextension.instrumentations.zio

import zio.Unsafe
import zio.metrics.MetricKey
import zio.metrics.MetricKeyType
import zio.metrics.MetricPair

import scala.reflect.runtime.{ universe => ru }

object ConcurrentMetricRegistryClient {

  def snapshot(): Set[MetricPair.Untyped] =
    snapshotCall()

  def get[Type <: MetricKeyType](key: MetricKey[Type]): MetricHook[key.keyType.In, key.keyType.Out] = {
    val hook = getMethod.invoke(metricsRegistry, key, unsafe)
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

  case class MetricHook[In, Out](
    update: In => Unit,
    get: () => Out,
    modify: In => Unit
  )

  private lazy val runtimeMirror = ru.runtimeMirror(getClass.getClassLoader)

  private lazy val unsafe = {
    val runtimeMirror  = ru.runtimeMirror(getClass.getClassLoader)
    val moduleSymbol   = runtimeMirror.staticModule("zio.Unsafe$")
    val moduleMirror   = runtimeMirror.reflectModule(moduleSymbol)
    val moduleInstance = moduleMirror.instance
    val instanceMirror = runtimeMirror.reflect(moduleInstance)
    val methodSymbol   = instanceMirror.symbol.info.member(ru.TermName("unsafe")).asTerm.getter.asMethod
    val methodMirror   = instanceMirror.reflectMethod(methodSymbol)
    methodMirror.apply().asInstanceOf[zio.Unsafe]
  }

  private lazy val metricsRegistry = {
    val moduleSymbol   = runtimeMirror.staticModule("zio.internal.metrics.package$")
    val moduleMirror   = runtimeMirror.reflectModule(moduleSymbol)
    val moduleInstance = moduleMirror.instance
    val instanceMirror = runtimeMirror.reflect(moduleInstance)
    val methodSymbol   = instanceMirror.symbol.info.member(ru.TermName("metricRegistry")).asTerm.getter.asMethod
    val methodMirror   = instanceMirror.reflectMethod(methodSymbol)
    methodMirror.apply()
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
}
