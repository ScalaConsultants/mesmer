package io.scalac.mesmer.extension.util

import io.scalac.mesmer.extension.util.Tree.NonRoot._
import io.scalac.mesmer.extension.util.Tree.{ NonRoot, Root }
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class TreeBuilderTest extends AnyFlatSpec with Matchers {

  implicit val stringPartialOrdering: PartialOrdering[String] = new PartialOrdering[String] {
    def tryCompare(x: String, y: String): Option[Int] = (x, y) match {
      case _ if x.equals(y)     => Some(0)
      case _ if x.startsWith(y) => Some(1)
      case _ if y.startsWith(x) => Some(-1)
      case _                    => None
    }

    def lteq(x: String, y: String): Boolean = tryCompare(x, y).fold(false)(_ < 0)
  }

  private def builder: Root[String, String] = Tree.builder[String, String].asInstanceOf[Root[String, String]]

  "TreeBuilder" should "set element as root node for empty tree builder" in {
    val sut = builder

    sut.insert("ala")

    sut.root should be(Some("ala", "ala"))
  }

  it should "have no root element and no children when empty" in {
    val sut = builder

    sut.children should have size (0)
    sut.root should be(None)
  }

  it should "swap root element" in {
    val sut    = builder
    val first  = "aaa"
    val second = "aaab"

    sut
      .insert(second)
      .insert(first)

    sut.root should be(Some(first, first))
    sut.children should contain theSameElementsAs (Seq(NonRoot.leaf(second)))
  }

  it should "create entry with no root when elements are not comparable" in {
    val sut = builder

    sut
      .insert("aaa")
      .insert("aab")

    sut.root should be(None)
    sut.children should contain theSameElementsAs (Seq(NonRoot.leaf("aaa"), NonRoot.leaf("aab")))
  }

  it should "add root node if it is a parent of all other elements" in {
    val sut = builder

    sut
      .insert("aaa")
      .insert("aab")
      .insert("aa")

    sut.root should be(Some("aa", "aa"))
    sut.children should contain theSameElementsAs (Seq(NonRoot.leaf("aaa"), NonRoot.leaf("aab")))
  }

  it should "append element if it's not a parent of all / child of any existing nodes" in {
    val sut = builder
      .insert("aaa")
      .insert("aab")

    sut
      .insert("aac")

    sut.root should be(None)
    sut.children should contain theSameElementsAs (Seq(NonRoot.leaf("aaa"), NonRoot.leaf("aab"), NonRoot.leaf("aac")))
  }

  it should "append element to a child" in {
    val sut = builder
      .insert("aaa")
      .insert("aab")

    sut.insert("aaab")

    sut.root should be(None)
    sut.children should contain theSameElementsAs (Seq(NonRoot.withChildren("aaa")("aaab"), NonRoot.leaf("aab")))
  }

  it should "proxy all children with new element" in {
    val sut = builder
      .insert("a")
      .insert("aab")
      .insert("aac")

    sut.insert("aa")

    sut.root should be(Some("a", "a"))
    sut.children should contain theSameElementsAs (Seq(NonRoot.withChildren("aa")("aab", "aac")))
  }

  it should "proxy all children with old root" in {
    val sut = builder
      .insert("aa")
      .insert("aab")
      .insert("aac")

    sut.insert("a")

    sut.root should be(Some("a", "a"))
    sut.children should contain theSameElementsAs (Seq(NonRoot.withChildren("aa")("aab", "aac")))
  }

  it should "insert element to only one child" in {
    val sut = builder
      .insert("aa")
      .insert("ab")
      .insert("ac")
      .insert("aab")
      .insert("aac")
      .insert("aad")
      .insert("aba")
      .insert("abb")
      .insert("a")

    val expectedChildren =
      Seq(withChildren("aa")("aab", "aac", withChildren("aad")("aada")), withChildren("ab")("aba", "abb"), leaf("ac"))

    sut.insert("aada")

    sut.root should be(Some("a", "a"))
    sut.children should contain theSameElementsAs (expectedChildren)

  }

  it should "not allow duplicates in a root" in {
    val sut = builder
      .insert("aa")
      .insert("aa")

    sut.root should be(Some("aa", "aa"))
    sut.children should be(empty)
  }

  it should "not allow duplicates in children when root absent" in {
    val sut = builder
      .insert("aa")
      .insert("bb")
      .insert("bb")

    sut.root should be(None)
    sut.children should contain theSameElementsAs (Seq(leaf("aa"), leaf("bb")))
  }

  it should "not allow duplicates in grandchildren" in {
    val sut = builder
      .insert("aa")
      .insert("aaa")
      .insert("aaaa")
      .insert("aaaa")

    sut.root should be(Some("aa", "aa"))
    sut.children should contain theSameElementsAs (Seq(withChildren("aaa")("aaaa")))
  }

  it should "remove element" in {
    val sut = builder
      .insert("a")
      .insert("aa")
      .insert("ab")

    sut.remove("aa")

    sut.root should be(Some("a", "a"))
    sut.children should be(Seq(leaf("ab")))
  }

  it should "remove element for child node" in {
    val sut = builder
      .insert("a")
      .insert("aa")
      .insert("ab")
      .insert("aaa")

    sut.remove("aaa")

    sut.root should be(Some("a", "a"))
    sut.children should contain theSameElementsAs (Seq(leaf("ab"), leaf("aa")))
  }

  it should "normalize tree after remove" in {
    val sut = builder
      .insert("aa")
      .insert("bb")

    sut.remove("aa")

    sut.root should be(Some("bb", "bb"))
    sut.children should be(empty)
  }

  it should "remove element deep nested in a tree" in {
    val sut = builder
      .insert("aa")
      .insert("ab")
      .insert("ac")
      .insert("aab")
      .insert("aac")
      .insert("aad")
      .insert("aba")
      .insert("abb")
      .insert("a")
      .insert("aada")

    val expectedChildren =
      Seq(withChildren("aa")("aab", "aac", "aad"), withChildren("ab")("aba", "abb"), leaf("ac"))

    sut.remove("aada")

    sut.root should be(Some("a", "a"))
    sut.children should contain theSameElementsAs (expectedChildren)
  }

  it should "build immutable tree from root node" in {
    val sut = builder
      .insert("a")
      .insert("aa")
      .insert("ab")

    val result = sut.build((_, x) => Some(x))

    val expected = Tree.tree("a", Tree.leaf("aa"), Tree.leaf("ab"))

    result should be(Some(expected))
  }

  it should "build immutable tree with nodes filtered" in {
    val sut = builder
      .insert("a")
      .insert("aa")
      .insert("ab")

    val result = sut.build((_, x) => if (x == "aa") None else Some(x))

    val expected = Tree.tree("a", Tree.leaf("ab"))

    result should be(Some(expected))
  }

  it should "not build tree when root is not defined" in {
    val sut = builder
      .insert("aa")
      .insert("bb")
      .insert("cc")

    val result = sut.build((_, x) => Some(x))

    result should be(None)
  }
  it should "not build tree when root node is filtered out" in {
    val sut = builder
      .insert("aa")
      .insert("ab")
      .insert("ac")
      .insert("a")

    val result = sut.build((_, x) => if (x == "a") None else Some(x))

    result should be(None)
  }

  it should "transform values to different type" in {
    val sut = builder
      .insert("1")
      .insert("12")
      .insert("1234bl")
      .insert("13")
      .insert("13pl")
      .insert("1aa")

    val result = sut.build((_, x) => x.toIntOption)

    val expected = Tree.tree(1, Tree.leaf(12), Tree.leaf(13))

    result should be(Some(expected))
  }

  it should "build deeply nested builder" in {
    val sut = builder
      .insert("a")
      .insert("aa")
      .insert("ab")
      .insert("ac")
      .insert("ad")
      .insert("aaa")
      .insert("aab")
      .insert("aac")
      .insert("aba")
      .insert("abb")
      .insert("aca")
      .insert("acb")
      .insert("aaaa")
      .insert("aaab")
      .insert("aaac")
      .insert("aaaaa")
      .insert("aaaab")
      .insert("aaaac")

    import Tree._
    val expected = tree(
      "a",
      tree(
        "aa",
        tree("aaa", tree("aaaa", leaf("aaaaa"), leaf("aaaab"), leaf("aaaac")), leaf("aaab"), leaf("aaac")),
        tree("aab"),
        tree("aac")
      ),
      tree("ab", leaf("aba"), leaf("abb")),
      tree("ac", leaf("aca"), leaf("acb")),
      tree("ad")
    )

    sut.build((_, x) => Some(x)) should be(Some(expected))

  }

  it should "transform deeply nested builder" in {
    val sut = builder
      .insert("a")
      .insert("aa")
      .insert("ab")
      .insert("ac")
      .insert("ad")
      .insert("aaa")
      .insert("aab")
      .insert("aac")
      .insert("aba")
      .insert("abb")
      .insert("aca")
      .insert("acb")
      .insert("aaaa")
      .insert("aaab")
      .insert("aaac")
      .insert("aaaaa")
      .insert("aaaab")
      .insert("aaaac")

    import Tree._
    val expected = tree(
      "a",
      tree(
        "aa",
        tree("aaa", tree("aaaa", leaf("aaaaa")))
      ),
      leaf("aba"),
      leaf("aca")
    )

    val transform: (String, String) => Option[String] = (_, x) => if (x.endsWith("a")) Some(x) else None

    sut.build(transform) should be(Some(expected))

  }

}
