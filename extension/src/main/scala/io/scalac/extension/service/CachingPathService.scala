package io.scalac.extension.service

import java.util.LinkedHashMap
import java.util.Map

import scala.annotation.tailrec
import scala.jdk.CollectionConverters._

import io.scalac.core.model.Path
import io.scalac.extension.config.CachingConfig

class CachingPathService(cachingConfig: CachingConfig) extends PathService {

  import PathService._

  private[service] val cache = new LinkedHashMap[String, Unit](cachingConfig.maxEntries, 0.75f, true) {
    override def removeEldestEntry(eldest: Map.Entry[String, Unit]): Boolean =
      this.size() > cachingConfig.maxEntries
  }.asScala

  def template(path: Path): Path = {

    @tailrec
    def replaceInPath(offset: Int, replacements: Vector[(Int, Int, String)]): Vector[(Int, Int, String)] = {
      var nextIndex = path.indexOf('/', offset)
      if (nextIndex <= -1) {
        nextIndex = path.length
      }
      if (offset >= path.length) {
        replacements
      } else {
        path.substring(offset, nextIndex) match {
          case subs if cache.contains(subs) =>
            replaceInPath(nextIndex + 1, replacements)

          case subs if numberRegex.findPrefixOf(subs).isDefined =>
            replaceInPath(nextIndex + 1, replacements :+ (offset, nextIndex, numberTemplate))
          case subs if subs.length == 36 && uuidRegex.findPrefixOf(subs).isDefined =>
            replaceInPath(nextIndex + 1, replacements :+ (offset, nextIndex, uuidTemplate))
          case subs =>
            cache.put(subs, ())
            replaceInPath(nextIndex + 1, replacements)
        }
      }
    }

    replaceInPath(if (path.startsWith("/")) 1 else 0, Vector.empty) match {
      case Vector() => path
      case replacements =>
        val builder = new StringBuilder(path.length)
        val last = replacements.foldLeft(0) { case (begin, repl) =>
          builder.append(path.substring(begin, repl._1)).append(repl._3)
          repl._2
        }
        builder.append(path.substring(last, path.length))
        builder.toString()
    }
  }
}
