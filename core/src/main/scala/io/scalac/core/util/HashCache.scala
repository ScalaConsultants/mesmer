package io.scalac.core.util

trait HashCache {
  this: Product =>

  override lazy val hashCode: Int = scala.util.hashing.MurmurHash3.productHash(this)
}
