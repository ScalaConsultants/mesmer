package io.scalac.extension.service

import io.scalac.extension.model.Path

import java.util.{ LinkedHashMap, Map }
import scala.annotation.tailrec
import scala.jdk.CollectionConverters._

class CachingPathService(val cacheCapacity: Int) extends PathService {

  private[this] val cache = new LinkedHashMap[String, Unit](cacheCapacity, 0.75f, true) {
    override def removeEldestEntry(eldest: Map.Entry[String, Unit]): Boolean =
      this.size() > cacheCapacity
  }.asScala

  def cachedElements: Set[String] = cache.keySet.toSet

  private var hit: Int = 0

  def cacheHit(): Int = hit

  private val uuid   = """^[\da-fA-F]{8}-[\da-fA-F]{4}-[\da-fA-F]{4}-[\da-fA-F]{4}-[\da-fA-F]{12}$""".r
  private val number = """^[+-]?\d+\.?\d*$""".r

  val numberTemplate = "{num}"
  val uuidTemplate   = "{uuid}"

  override def template(path: Path): Path = {

    @tailrec
    def replaceInPath(offset: Int, replacements: Vector[(Int, Int, String)]): Vector[(Int, Int, String)] = {
      var nextIndex = path.indexOf('/', offset)
      if (nextIndex <= -1) {
        nextIndex = path.size
      }
      if (offset >= path.size) {
        replacements
      } else {
        path.substring(offset, nextIndex) match {
          case subs if cache.contains(subs) =>
            hit += 1
            replaceInPath(nextIndex + 1, replacements)

          case subs if number.findPrefixOf(subs).isDefined => {
            replaceInPath(nextIndex + 1, replacements :+ (offset, nextIndex, numberTemplate))
          }
          case subs if (subs.length == 36 && uuid.findPrefixOf(subs).isDefined) => {
            replaceInPath(nextIndex + 1, replacements :+ (offset, nextIndex, uuidTemplate))
          }
          case subs => {
            cache.put(subs, ())
            replaceInPath(nextIndex + 1, replacements)
          }
        }
      }
    }

    replaceInPath(if (path.startsWith("/")) 1 else 0, Vector.empty) match {
      case Vector() => path
      case replacements => {
        val builder = new StringBuilder(path.size)
        val last = replacements.foldLeft(0) {
          case (begin, repl) => {
            builder.append(path.substring(begin, repl._1)).append(repl._3)
            repl._2
          }
        }
        builder.append(path.substring(last, path.length))
        builder.toString()
      }
    }
  }
}
