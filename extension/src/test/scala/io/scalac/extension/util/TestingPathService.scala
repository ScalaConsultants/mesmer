package io.scalac.extension.util

import io.scalac.extension.model.Path
import io.scalac.extension.service.PathService

import scala.annotation.tailrec
import scala.collection.mutable.ListBuffer

class TestingPathService extends PathService {
  private[this] val operations: ListBuffer[String => Option[String]] = ListBuffer.empty
  @volatile
  private[this] var _count = 0L

  override def template(path: Path): Path = {
    _count += 1L

    @tailrec
    def internalTemplate(list: List[String => Option[String]], path: String): String = list match {
      case Nil => path
      case head :: tail => {
        head(path) match {
          case None    => internalTemplate(tail, path)
          case Some(x) => x
        }
      }
    }
    internalTemplate(operations.toList, path)
  }

  def count(): Long = _count

  def reset(): Unit = {
    _count = 0L
    operations.clear()
  }

}
