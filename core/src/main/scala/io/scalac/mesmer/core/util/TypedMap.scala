package io.scalac.mesmer.core.util
import scala.collection.mutable.{ Map => MutableMap }
/*
 * Inspired by akka.util.TypedMultiMap
 * K needs to be any ref for TypedMap to have access to key.type
 */

class TypedMap[K <: AnyRef, KV[_ <: K]] private (private val map: Map[K, Any]) {

  def insert(key: K)(value: KV[key.type]): TypedMap[K, KV] =
    new TypedMap[K, KV](map + (key -> value))

  def get(key: K): Option[KV[key.type]] = map.get(key).asInstanceOf[Option[KV[key.type]]]
}

object TypedMap {
  private val _empty                                  = new TypedMap[Nothing, Nothing](Map.empty)
  def apply[K <: AnyRef, KV[_ <: K]]: TypedMap[K, KV] = _empty.asInstanceOf[TypedMap[K, KV]]
}

class MutableTypedMap[K <: AnyRef, KV[_ <: K]] private (private val map: MutableMap[K, Any]) { self =>
  def getOrCreate(key: K)(init: => KV[key.type]): KV[key.type] =
    map
      .get(key)
      .orElse(map.synchronized {
        Some(map.getOrElseUpdate(key, init))
      })
      .get // this should never fail as we produce Some in orElse clause
      .asInstanceOf[KV[key.type]]

  def get(key: K): Option[KV[key.type]] = map.get(key).asInstanceOf[Option[KV[key.type]]]

  def size: Int = map.size

}

object MutableTypedMap {
  def apply[K <: AnyRef, KV[_ <: K]]: MutableTypedMap[K, KV] = new MutableTypedMap[K, KV](MutableMap.empty)
}
