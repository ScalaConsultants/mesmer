package io.scalac.extension.metric

import org.scalatest.Inspectors
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable.ListBuffer

class CachingMonitorTest extends AnyFlatSpec with Matchers with Inspectors {

  case class TestBound(labels: String)
  class TestBindable extends Bindable[String] {
    override type Bound = TestBound

    private[this] val _binds: ListBuffer[String] = ListBuffer.empty

    override def bind(labels: String): Bound = {
      _binds += labels
      TestBound(labels)
    }

    def binds: List[String] = _binds.toList
  }
  type Fixture = TestBindable

  def test(body: Fixture => Any): Any =
    body(new TestBindable)

  "CachingMonitor" should "proxy to wrapped monitor" in test { testBindable =>
    val sut = new CachingMonitor[String, TestBound, TestBindable](testBindable)

    val labels = List.tabulate(10)(num => s"label_${num}")

    labels.foreach(sut.bind)

    testBindable.binds should contain theSameElementsInOrderAs (labels)
  }

  it should "return same instance when keys repeat" in test { testBindable =>
    val sut       = new CachingMonitor[String, TestBound, TestBindable](testBindable)
    val label     = "label"
    val labels    = List.fill(10)(label)
    val instances = labels.map(sut.bind)

    testBindable.binds should have size (1)
    testBindable.binds shouldBe List(label)

    forAll(instances.tail)(_ should be theSameInstanceAs (instances.head))
  }

}
