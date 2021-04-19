package io.scalac.extension.metric

import org.scalatest.Inspectors
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable.ListBuffer

import io.scalac.core.LabelSerializable
import io.scalac.core.model._
import io.scalac.extension.config.CachingConfig

class CachingMonitorTest extends AnyFlatSpec with Matchers with Inspectors {

  case class TestLabels(label: String) extends LabelSerializable {
    def serialize: RawLabels = Seq("label" -> label)
  }

  case class TestBound(labels: TestLabels) extends Bound {
    private[this] var _unbound = false
    def unbind(): Unit         = _unbound = true
    def unbound: Boolean       = _unbound
  }

  class TestBindable extends Bindable[TestLabels, TestBound]() {

    private[this] val _binds: ListBuffer[TestLabels] = ListBuffer.empty

    def bind(labels: TestLabels): TestBound = {
      _binds += labels
      TestBound(labels)
    }

    def binds: List[TestLabels] = _binds.toList
  }

  type Fixture = TestBindable

  def test[T](body: Fixture => T): T =
    body(new TestBindable)

  "CachingMonitor" should "proxy to wrapped monitor" in test { testBindable =>
    val sut = CachingMonitor(testBindable, CachingConfig.empty)

    val labels = List.tabulate(10)(num => s"label_$num").map(TestLabels.apply)

    labels.foreach(sut.bind)

    testBindable.binds should contain theSameElementsInOrderAs labels
  }

  it should "return same instance when keys repeat" in test { testBindable =>
    val sut       = CachingMonitor(testBindable, CachingConfig.empty)
    val label     = TestLabels("label")
    val labels    = List.fill(10)(label)
    val instances = labels.map(sut.bind)

    testBindable.binds should have size 1
    testBindable.binds shouldBe List(label)

    forAll(instances.tail)(_ should be theSameInstanceAs instances.head)
  }

  it should "evict elements when cache limit is hit" in test { testBindable =>
    val cacheSize = 5
    val sut       = CachingMonitor(testBindable, CachingConfig(cacheSize))
    val labels    = List.tabulate(cacheSize + 1)(num => s"label_$num").map(TestLabels.apply)

    val instances = labels.map(sut.bind)
    instances.head.unbound shouldBe true
    forAll(instances.tail)(_.unbound shouldBe false)
    sut.cachedMonitors should have size cacheSize
    sut.cachedMonitors.keys should contain theSameElementsAs labels.tail
  }

  it should "evict monitors in LRU manner" in test { testBindable =>
    val cacheSize                              = 5
    val sut                                    = CachingMonitor(testBindable, CachingConfig(cacheSize))
    val labels @ firstLabel :: _ :: labelsTail = List.tabulate(cacheSize)(num => s"label_$num").map(TestLabels.apply)
    val additionalLabel                        = TestLabels("evicting_label")

    val instances @ firstInstance :: secondInstance :: instancesTail = labels.map(sut.bind)
    sut.bind(firstLabel)
    //
    forAll(instances)(_.unbound shouldBe false)
    sut.cachedMonitors.keys should contain theSameElementsAs labels
    val additionalInstance = sut.bind(additionalLabel)

    secondInstance.unbound shouldBe true

    forAll(firstInstance :: additionalInstance :: instancesTail)(_.unbound shouldBe false)

    sut.cachedMonitors.keys should contain theSameElementsAs (firstLabel :: additionalLabel :: labelsTail)
  }
}
