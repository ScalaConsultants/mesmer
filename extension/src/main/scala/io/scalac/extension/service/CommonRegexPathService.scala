package io.scalac.extension.service

import scala.annotation.tailrec

import io.scalac.core.model._

object CommonRegexPathService extends PathService {

  import PathService._

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
          case numberRegex(_*) =>
            replaceInPath(nextIndex + 1, replacements :+ (offset, nextIndex, numberTemplate))

          case subs if subs.length == 36 =>
            subs match {
              case uuidRegex(_*) =>
                //next must be slash, so skip it
                replaceInPath(nextIndex + 1, replacements :+ (offset, nextIndex, uuidTemplate))
              case _ => replaceInPath(nextIndex + 1, replacements)
            }
          case _ => replaceInPath(nextIndex + 1, replacements)
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
