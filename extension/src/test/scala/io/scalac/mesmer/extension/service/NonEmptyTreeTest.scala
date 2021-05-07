package io.scalac.mesmer.extension.service

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class NonEmptyTreeTest extends AnyFlatSpec with Matchers {

  implicit val PrefixPartialOrdering: PartialOrdering[String] = new PartialOrdering[String] {
    def tryCompare(x: String, y: String): Option[Int] = (x, y) match {
      case _ if x == y          => Some(0)
      case _ if y.startsWith(x) => Some(-1)
      case _ if x.startsWith(y) => Some(1)
      case _                    => None
    }

    def lteq(x: String, y: String): Boolean = y.startsWith(x)
  }

  "Tree" should "construct tree with depth 2" in {
    val rootValue = "x"
    val children  = Seq("xa", "xb", "xc", "xd", "xe")

    val expectedTree: NonEmptyTree[String] =
      NonEmptyTree.withChildren[String](rootValue)(children.map(NonEmptyTree.apply[String]): _*)

    val result = children.foldLeft(NonEmptyTree(rootValue))(_.insert(_))

    result should be(expectedTree)
  }

  it should "replace root" in {
    val expectedTree: NonEmptyTree[String] =
      NonEmptyTree.withChildren("a")(NonEmptyTree("aa"), NonEmptyTree("ab"), NonEmptyTree("ac"))

    val result = NonEmptyTree("aa").insert("a").insert("ab").insert("ac")

    result should be(expectedTree)
  }

  //TODO current implementation doesn't support replacing all children with different parent
  it should "replace parent for elements" ignore {
    val expectedTree: NonEmptyTree[String] =
      NonEmptyTree.withChildren("a")(NonEmptyTree.withChildren("aa")(NonEmptyTree("aab"), NonEmptyTree("aac")))

    val result = NonEmptyTree("a").insert("aab").insert("aac").insert("aa")

    result should be(expectedTree)
  }

  it should "insert elements in defined order" in {
    val expectedTree = NonEmptyTree.withChildren("a")(
      NonEmptyTree.withChildren("ab")(NonEmptyTree("aba"), NonEmptyTree("abc"), NonEmptyTree("abd")),
      NonEmptyTree.withChildren("ac")(NonEmptyTree("aca"), NonEmptyTree("acb"), NonEmptyTree("acd")),
      NonEmptyTree.withChildren("ad")(NonEmptyTree("ada"), NonEmptyTree("adb"), NonEmptyTree("adc"))
    )

    val elements = Seq("ab", "ac", "ad", "aba", "aca", "ada", "abc", "acb", "adb", "abd", "acd", "adc", "a")

    NonEmptyTree.fromSeq(elements) should be(Some(expectedTree))
  }

  it should "allow for duplicates" in {
    val expectedTree = NonEmptyTree.withChildren("a")(NonEmptyTree("aa"), NonEmptyTree("aa"), NonEmptyTree("aa"))

    val result = NonEmptyTree("a").insert("aa").insert("aa").insert("aa")

    result should be(expectedTree)
  }

  it should "accumulate values from each children" in {
    val expectedResult = "a->(b->(),c->(),d->())"
    val tree           = NonEmptyTree.withChildren("a")(NonEmptyTree("b"), NonEmptyTree("c"), NonEmptyTree("d"))

    val result = tree.foldLeft[String] { case (agg, cur) =>
      s"$cur->${agg.mkString("(", ",", ")")}"
    }("")

    result should be(expectedResult)
  }

  it should "accumulate values from each children 2 " in {
    val expectedResult = "a->(b->(e->(f->(),g->(h->(),i->())),j->()),c->(k->(),l->(m->(),n->(o->()))),d->())"
    val tree = NonEmptyTree.withChildren("a")(
      NonEmptyTree.withChildren("b")(
        NonEmptyTree
          .withChildren("e")(NonEmptyTree("f"), NonEmptyTree.withChildren("g")(NonEmptyTree("h"), NonEmptyTree("i"))),
        NonEmptyTree("j")
      ),
      NonEmptyTree.withChildren("c")(
        NonEmptyTree("k"),
        NonEmptyTree.withChildren("l")(
          NonEmptyTree("m"),
          NonEmptyTree.withChildren("n")(NonEmptyTree("o"))
        )
      ),
      NonEmptyTree("d")
    )

    val result = tree.foldLeft[String] { case (agg, cur) =>
      s"$cur->${agg.mkString("(", ",", ")")}"
    }("")

    result should be(expectedResult)
  }

}
