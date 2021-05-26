package io.scalac.mesmer.extension.util

import io.scalac.mesmer.extension.util.Tree._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class TreeTest extends AnyFlatSpec with Matchers {

  "TreeTest" should "fold values bottom up for same type F-algebra" in {

    val sut = tree(10, leaf(1), leaf(2), leaf(3))

    val alg: TreeF[Int, Int] => Int = { case TreeF(value, children) =>
      value * children.reduceOption(_ + _).getOrElse(1)
    }

    sut.unfix.foldRight(alg) should be(60)
  }

  it should "fold values bottom for different type F-algebra" in {
    val sut = tree(10, leaf(1), leaf(2), leaf(3))

    val alg: TreeF[Int, String] => String = {
      case TreeF(value, Vector()) => s"{$value}"
      case TreeF(value, children) =>
        s"($value, ${children.mkString("[", ",", "]")})"

    }

    val result = sut.unfix.foldRight(alg)

    result should be("(10, [{1},{2},{3}])")
  }
}
