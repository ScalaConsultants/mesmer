package io.scalac.extension.service

import io.scalac.extension.model.Path

import java.util.{ LinkedHashMap, Map }
import scala.annotation.tailrec
import scala.jdk.CollectionConverters._

class AsyncCachingPathServicePrefix(val cacheCapacity: Int) extends PathService {

  private[this] val cache = new LinkedHashMap[String, Unit](cacheCapacity, 1.0f, true) {
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

        if (replacements.isEmpty) {}
        replacements
      } else {
        val str = path.substring(offset, nextIndex)
        str match {
          case subs if cache.contains(subs) =>
            hit += 1
            replaceInPath(nextIndex + 1, replacements)

          case number(_*) => {
            //next must be slash, so skip it
            replaceInPath(nextIndex + 1, replacements :+ (offset, nextIndex, numberTemplate))
          }
          case subs if subs.length == 36 => {
            subs match {
              case uuid(_*) => {
                replaceInPath(nextIndex + 1, replacements :+ (offset, nextIndex, uuidTemplate))
              }
              case _ => {
                cache.put(subs, ())
                replaceInPath(nextIndex + 1, replacements)
              }
            }
          }
          case subs => {
            cache.put(subs, ())
            replaceInPath(nextIndex + 1, replacements)
          }
        }
      }
    }

    replaceInPath(1, Vector.empty) match {
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

class AsyncCachingPathService(val cacheCapacity: Int) extends PathService {

  private[this] val cache = new LinkedHashMap[String, Unit](cacheCapacity, 1.0f, true) {
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

          case number(_*) => {
            //next must be slash, so skip it
            replaceInPath(nextIndex + 1, replacements :+ (offset, nextIndex, numberTemplate))
          }
          case subs if subs.length == 36 => {
            subs match {
              case uuid(_*) => {
                replaceInPath(nextIndex + 1, replacements :+ (offset, nextIndex, uuidTemplate))
              }
              case _ => {
                cache.put(subs, ())
                replaceInPath(nextIndex + 1, replacements)
              }
            }
          }
          case subs => {
            cache.put(subs, ())
            replaceInPath(nextIndex + 1, replacements)
          }
        }
      }
    }

    replaceInPath(1, Vector.empty) match {
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

object CommonRegexPathService extends PathService {
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
          case number(_*) => {
            //next must be slash, so skip it
            replaceInPath(nextIndex + 1, replacements :+ (offset, nextIndex, numberTemplate))
          }
          case subs if subs.length == 36 => {
            subs match {
              case uuid(_*) => {
                //next must be slash, so skip it
                replaceInPath(nextIndex + 1, replacements :+ (offset, nextIndex, uuidTemplate))
              }
              case _ => replaceInPath(nextIndex + 1, replacements)
            }
          }
          case _ => replaceInPath(nextIndex + 1, replacements)
        }
      }
    }

    replaceInPath(0, Vector.empty) match {
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

object OldCommonRegexPathService extends PathService {
  private val uuid   = """^[\da-fA-F]{8}-[\da-fA-F]{4}-[\da-fA-F]{4}-[\da-fA-F]{4}-[\da-fA-F]{12}$""".r
  private val number = """^[+-]?\d+\.?\d*$""".r

  val numberTemplate         = "{num}"
  val uuidTemplate           = "{uuid}"
  private val detectionChain = List((number, numberTemplate), (uuid, uuidTemplate))

  override def template(path: Path): Path =
    path
      .split('/')
      .map { segment =>
        detectionChain.find {
          case (regex, _) => regex.findPrefixOf(segment).isDefined
        }.map(_._2).getOrElse(segment)
      }
      .mkString("", "/", if (path.endsWith("/")) "/" else "")
}


object DummyCommonRegexPathService extends PathService {
  private val uuid   = """^[\da-fA-F]{8}-[\da-fA-F]{4}-[\da-fA-F]{4}-[\da-fA-F]{4}-[\da-fA-F]{12}$""".r
  private val number = """^[+-]?\d+\.?\d*$""".r

  val numberTemplate         = "{num}"
  val uuidTemplate           = "{uuid}"
  private val detectionChain = List((number, numberTemplate), (uuid, uuidTemplate))

  override def template(path: Path): Path = path match {
    case get if get.contains("balance") => "/api/v1/account/{uuid}/balance"
    case withdraw if withdraw.contains("balance") => "/api/v1/account/{uuid}/withdraw/{num}"
    case deposit if deposit.contains("balance") => "/api/v1/account/{uuid}/deposit/{num}"
    case _ => "other"
  }
}
