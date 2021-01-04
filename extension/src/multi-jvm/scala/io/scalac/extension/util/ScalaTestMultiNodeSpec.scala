package io.scalac.extension.util

import akka.remote.testkit.{ MultiNodeSpec, MultiNodeSpecCallbacks }
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

trait ScalaTestMultiNodeSpec extends MultiNodeSpecCallbacks with AnyWordSpecLike with Matchers with BeforeAndAfterAll {
  self: MultiNodeSpec =>

  override def beforeAll(): Unit = {
    multiNodeSpecBeforeAll()
    super.beforeAll()
  }
  override def afterAll(): Unit = {
    super.afterAll()
    multiNodeSpecAfterAll()
  }

  override protected implicit def convertToWordSpecStringWrapper(
    s: String
  ): WordSpecStringWrapper =
    new WordSpecStringWrapper(s"$s (on node '${self.myself.name}', $getClass)")

}
