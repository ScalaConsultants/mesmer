package io.scalac.extension.util

import akka.actor.typed.ActorRef
import org.scalatest.matchers.{ MatchResult, Matcher }

import java.util.UUID
import scala.util.Random

trait TestOps {

  def createUniqueId: String = UUID.randomUUID().toString

  def sameOrParent(ref: ActorRef[_]): Matcher[ActorRef[_]] = left => {
    val test = ref.path == left.path || left.path.parent == ref.path

    MatchResult(test, s"${ref} is not same or parent of ${left}", s"${ref} is same as or parent of ${left}")
  }

  def randomString(length: Int): String = {

    val charArray = Array.fill(length) {
      val charCode = Random.nextInt(26) + 97
      charCode.toChar
    }
    new String(charArray)
  }

}
